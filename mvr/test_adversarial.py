import unittest
from collections import defaultdict
from dispatch_simulation import Driver, Passenger, Order


def adversarial_dispatch(drivers, orders, availability=None, decline=None):
    availability = availability or (lambda d, o: True)
    decline = decline or (lambda d, o: False)
    served = defaultdict(int)
    last_assigned = {d.id: -1 for d in drivers}
    qualified_wait = defaultdict(int)
    decisions = []
    exhausted = 0

    for order in orders:
        qualified = [d for d in drivers if d.active and d.verified and d.zone == order.zone and availability(d, order)]
        ranked = sorted(qualified, key=lambda d: (-qualified_wait[d.id], served[d.id], last_assigned[d.id], d.id))
        winner = None
        attempts = []
        for candidate in ranked:
            attempts.append(candidate.id)
            if decline(candidate, order):
                continue
            winner = candidate
            break

        for d in qualified:
            if winner and d.id == winner.id:
                qualified_wait[d.id] = 0
            else:
                qualified_wait[d.id] += 1

        if winner is None:
            exhausted += 1
        else:
            served[winner.id] += 1
            last_assigned[winner.id] = order.id

        decisions.append({"order_id": order.id, "qualified": [d.id for d in qualified], "attempts": attempts, "winner": None if winner is None else winner.id})

    return served, qualified_wait, decisions, exhausted


class Milestone2AdversarialTests(unittest.TestCase):
    def setUp(self):
        self.drivers = [Driver(i, i % 5) for i in range(20)]
        self.passengers = [Passenger(i, i % 5) for i in range(200)]

    def test_hotspot_demand_does_not_starve_qualified_drivers(self):
        orders = [Order(i, i % 200, 0 if i < 8000 else i % 5) for i in range(10000)]
        served, waits, decisions, exhausted = adversarial_dispatch(self.drivers, orders)
        self.assertEqual(0, exhausted)
        zone0 = [d.id for d in self.drivers if d.zone == 0]
        self.assertTrue(all(served[d] > 0 for d in zone0))
        self.assertLessEqual(max(served[d] for d in zone0) - min(served[d] for d in zone0), 1)

    def test_offline_online_does_not_reset_qualified_wait_advantage(self):
        drivers = [Driver(0, 0), Driver(1, 0)]
        orders = [Order(i, 0, 0) for i in range(100)]
        def availability(d, o):
            if d.id == 0 and 20 <= o.id < 40:
                return False
            return True
        served, waits, decisions, exhausted = adversarial_dispatch(drivers, orders, availability=availability)
        self.assertEqual(0, exhausted)
        self.assertGreater(served[0], 0)
        self.assertGreater(served[1], 0)
        self.assertLessEqual(abs(served[0] - served[1]), 20)

    def test_decliner_cannot_block_fulfillment_when_alternative_exists(self):
        drivers = [Driver(0, 0), Driver(1, 0), Driver(2, 0)]
        orders = [Order(i, 0, 0) for i in range(500)]
        served, waits, decisions, exhausted = adversarial_dispatch(drivers, orders, decline=lambda d, o: d.id == 0)
        self.assertEqual(0, exhausted)
        self.assertEqual(0, served[0])
        self.assertGreater(served[1], 0)
        self.assertGreater(served[2], 0)

    def test_no_duplicate_assignment_under_sequential_attempts(self):
        drivers = [Driver(i, 0) for i in range(5)]
        orders = [Order(i, 0, 0) for i in range(1000)]
        _, _, decisions, _ = adversarial_dispatch(drivers, orders, decline=lambda d, o: d.id in (0, 1) and o.id % 3 == 0)
        self.assertTrue(all(x["winner"] is None or x["winner"] in x["qualified"] for x in decisions))
        self.assertEqual(len(decisions), len({x["order_id"] for x in decisions}))

    def test_total_supply_failure_is_explicit_exhaustion(self):
        drivers = [Driver(0, 0), Driver(1, 0)]
        orders = [Order(i, 0, 0) for i in range(50)]
        _, _, decisions, exhausted = adversarial_dispatch(drivers, orders, availability=lambda d, o: False)
        self.assertEqual(50, exhausted)
        self.assertTrue(all(x["winner"] is None for x in decisions))


if __name__ == "__main__":
    unittest.main()
