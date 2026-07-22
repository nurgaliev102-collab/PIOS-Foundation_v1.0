from __future__ import annotations

from dataclasses import dataclass
from collections import defaultdict
import argparse
import json
import random

SEED = 20260723
DRIVER_COUNT = 20
PASSENGER_COUNT = 200
ORDER_COUNT = 10_000

@dataclass(frozen=True)
class Driver:
    id: int
    zone: int
    active: bool = True
    verified: bool = True

@dataclass(frozen=True)
class Passenger:
    id: int
    home_zone: int

@dataclass(frozen=True)
class Order:
    id: int
    passenger_id: int
    zone: int


def build_world(seed: int = SEED):
    rng = random.Random(seed)
    drivers = [Driver(i, i % 5) for i in range(DRIVER_COUNT)]
    passengers = [Passenger(i, i % 5) for i in range(PASSENGER_COUNT)]
    orders = []
    for i in range(ORDER_COUNT):
        p = passengers[rng.randrange(PASSENGER_COUNT)]
        zone = p.home_zone if rng.random() < 0.8 else rng.randrange(5)
        orders.append(Order(i, p.id, zone))
    return drivers, passengers, orders


def simulate(seed: int = SEED):
    drivers, passengers, orders = build_world(seed)
    served = defaultdict(int)
    last_assigned = {d.id: -1 for d in drivers}
    decisions = []
    violations = []
    waits = []

    for order in orders:
        qualified = [d for d in drivers if d.active and d.verified and d.zone == order.zone]
        if not qualified:
            violations.append((order.id, "NO_QUALIFIED_DRIVER"))
            continue
        ranked = sorted(qualified, key=lambda d: (served[d.id], last_assigned[d.id], d.id))
        winner = ranked[0]
        min_served = min(served[d.id] for d in qualified)
        if served[winner.id] != min_served:
            violations.append((order.id, "FAIRNESS_MIN_SERVICE_BREACH"))
        wait = order.id - last_assigned[winner.id] if last_assigned[winner.id] >= 0 else order.id + 1
        waits.append(wait)
        served[winner.id] += 1
        last_assigned[winner.id] = order.id
        decisions.append({
            "order_id": order.id,
            "passenger_id": order.passenger_id,
            "zone": order.zone,
            "qualified_driver_ids": [d.id for d in qualified],
            "winner_driver_id": winner.id,
            "winner_service_count_after": served[winner.id],
        })

    counts = [served[d.id] for d in drivers]
    metrics = {
        "seed": seed,
        "drivers": len(drivers),
        "passengers": len(passengers),
        "orders": len(orders),
        "dispatch_decisions": len(decisions),
        "fulfillment": len(decisions) / len(orders),
        "starvation_driver_count": sum(1 for c in counts if c == 0),
        "max_min_service_gap": max(counts) - min(counts),
        "qualified_wait_mean_orders": sum(waits) / len(waits) if waits else None,
        "qualified_wait_max_orders": max(waits) if waits else None,
        "invariant_violations": len(violations),
        "service_counts": dict(sorted(served.items())),
    }
    return metrics, decisions, violations


def assert_15_invariants(metrics, decisions, violations):
    checks = {
        "I01_world_has_20_drivers": metrics["drivers"] == 20,
        "I02_world_has_200_passengers": metrics["passengers"] == 200,
        "I03_exactly_10000_orders": metrics["orders"] == 10_000,
        "I04_one_decision_per_fulfilled_order": metrics["dispatch_decisions"] == 10_000,
        "I05_full_fulfillment": metrics["fulfillment"] == 1.0,
        "I06_no_starved_driver": metrics["starvation_driver_count"] == 0,
        "I07_no_runtime_invariant_violation": len(violations) == 0,
        "I08_every_decision_has_qualified_set": all(x["qualified_driver_ids"] for x in decisions),
        "I09_winner_is_qualified": all(x["winner_driver_id"] in x["qualified_driver_ids"] for x in decisions),
        "I10_unique_order_decision": len({x["order_id"] for x in decisions}) == len(decisions),
        "I11_all_order_ids_covered": {x["order_id"] for x in decisions} == set(range(10_000)),
        "I12_valid_passenger_ids": all(0 <= x["passenger_id"] < 200 for x in decisions),
        "I13_valid_driver_ids": all(0 <= x["winner_driver_id"] < 20 for x in decisions),
        "I14_valid_zones": all(0 <= x["zone"] < 5 for x in decisions),
        "I15_bounded_global_service_gap": metrics["max_min_service_gap"] <= 50,
    }
    failed = [name for name, ok in checks.items() if not ok]
    if failed:
        raise AssertionError("Invariant failures: " + ", ".join(failed))
    return checks


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--decisions", help="Optional JSONL path for full decision log")
    args = parser.parse_args()
    metrics, decisions, violations = simulate()
    checks = assert_15_invariants(metrics, decisions, violations)
    metrics["invariants_passed"] = len(checks)
    print(json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True))
    if args.decisions:
        with open(args.decisions, "w", encoding="utf-8") as f:
            for decision in decisions:
                f.write(json.dumps(decision, ensure_ascii=False, sort_keys=True) + "\n")

if __name__ == "__main__":
    main()
