#!/usr/bin/env python3
"""Compare the LLM with and without RAG on the notebook's eval_set.csv.

Sends the buildings to POST /ai/ask twice (useRag false, then true), writes one row
per building to the output CSV and prints the error of each variant against
  - the baseline rule (always available), and
  - capacity_label, if you filled that column by hand (the only real ground truth).
Standard library only.

  python compare_rag.py rag_export/eval_set.csv --limit 40 --batch 5
"""
import argparse
import csv
import json
import math
import statistics
import sys
import urllib.request

FIELDS = {"footprint_clean": "footprintM2", "levels_clean": "numberOfLevels",
          "flats_clean": "numberOfFlats", "height_clean": "heightMeters"}


def num(value):
    try:
        x = float(value)
    except (TypeError, ValueError):
        return None
    return None if math.isnan(x) else x


def building(row):
    b = {"id": f"{row['osm_type']}/{row['osm_id']}"}
    for column, name in FIELDS.items():
        x = num(row.get(column))
        if x is not None:
            b[name] = int(x) if name in ("numberOfLevels", "numberOfFlats") else x
    return b


def post(url, body, timeout):
    request = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def run(url, buildings, use_rag, batch, timeout):
    results = {}
    for i in range(0, len(buildings), batch):
        part = buildings[i:i + batch]
        answer = post(url, {"requestType": "HOUSING_CAPACITY", "buildings": part, "useRag": use_rag}, timeout)
        for item in answer["results"]:
            results[item["id"]] = item
        print(f"  useRag={use_rag}: {min(i + batch, len(buildings))}/{len(buildings)}", file=sys.stderr)
    return results


def errors(pairs):
    """pairs: (predicted, reference). Returns (n, mean absolute error, median absolute % error, mean signed % error)."""
    pairs = [(p, r) for p, r in pairs if p is not None and r]
    if not pairs:
        return None
    abs_err = [abs(p - r) for p, r in pairs]
    pct = [(p - r) / r * 100 for p, r in pairs]
    return len(pairs), statistics.mean(abs_err), statistics.median(abs(x) for x in pct), statistics.mean(pct)


def show(title, stats):
    if stats is None:
        print(f"  {title}: no data")
        return
    n, mae, mape, bias = stats
    print(f"  {title}: n={n}, mean abs error {mae:.1f} residents, median abs error {mape:.0f}%, mean signed error {bias:+.0f}%")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("eval_csv")
    ap.add_argument("--url", default="http://localhost:8090/ai/ask")
    ap.add_argument("--limit", type=int, default=40, help="number of buildings (a local model is slow)")
    ap.add_argument("--batch", type=int, default=5)
    ap.add_argument("--timeout", type=float, default=600)
    ap.add_argument("--out", default="rag_comparison.csv")
    args = ap.parse_args()

    with open(args.eval_csv, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    # evenly spread over the file so every situation is likely to appear
    if len(rows) > args.limit:
        step = len(rows) / args.limit
        rows = [rows[int(i * step)] for i in range(args.limit)]
    buildings = [building(r) for r in rows]

    plain = run(args.url, buildings, False, args.batch, args.timeout)
    rag = run(args.url, buildings, True, args.batch, args.timeout)

    table = []
    for row, b in zip(rows, buildings):
        p, r = plain.get(b["id"], {}), rag.get(b["id"], {})
        label = num(row.get("capacity_label"))
        table.append({"id": b["id"], "tier": r.get("tier") or p.get("tier"), "baseline": r.get("baselineCapacity") or p.get("baselineCapacity"),
                      "label": label, "no_rag": p.get("capacity"), "rag": r.get("capacity"),
                      "no_rag_capped": p.get("cappedByCeiling"), "rag_capped": r.get("cappedByCeiling"),
                      "no_rag_error": p.get("error"), "rag_error": r.get("error"),
                      "no_rag_reasoning": p.get("reasoning"), "rag_reasoning": r.get("reasoning")})
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(table[0]))
        w.writeheader()
        w.writerows(table)

    print(f"\n{len(table)} buildings, written to {args.out}")
    for name in ("no_rag", "rag"):
        failed = sum(1 for t in table if t[name] is None)
        capped = sum(1 for t in table if t[name + "_capped"])
        print(f"{name}: {failed} without answer, {capped} limited by the ceiling")
    print("\nAgainst the baseline rule (shows how closely the model follows the rule, not whether it is right):")
    for name in ("no_rag", "rag"):
        show(name, errors([(t[name], t["baseline"]) for t in table]))
    labelled = [t for t in table if t["label"]]
    print(f"\nAgainst your hand labels ({len(labelled)} labelled):")
    if labelled:
        for name in ("baseline", "no_rag", "rag"):
            show(name, errors([(t[name], t["label"]) for t in labelled]))
    else:
        print("  none. Fill capacity_label in the eval CSV to get a real comparison.")


if __name__ == "__main__":
    main()
