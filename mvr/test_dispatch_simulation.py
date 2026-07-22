import unittest
from dispatch_simulation import simulate, assert_15_invariants

class DispatchMilestoneTest(unittest.TestCase):
    def test_10000_orders_pass_15_invariants(self):
        metrics, decisions, violations = simulate()
        checks = assert_15_invariants(metrics, decisions, violations)
        self.assertEqual(15, len(checks))
        self.assertEqual(0, metrics["invariant_violations"])
        self.assertEqual(10_000, metrics["dispatch_decisions"])

    def test_replay_is_deterministic(self):
        first = simulate()
        second = simulate()
        self.assertEqual(first, second)

if __name__ == "__main__":
    unittest.main()
