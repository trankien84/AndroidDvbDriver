"""Small, self-authored instruction fragments only; no firmware fixture."""
import unittest

from autoinlen_static import analyze_autoinlen, literal_writers
from gate_b_static import Machine


class AutoinlenTests(unittest.TestCase):
    def scan(self, hex_code):
        return literal_writers(bytes.fromhex(hex_code))

    def test_both_init_and_configuration_values_are_found(self):
        result = self.scan("90 e6 20 74 02 f0 90 e6 21 e4 f0 22 90 e6 20 74 04 f0 22")
        self.assertEqual([(w['address'], w['value']) for w in result['writes']],
                         [(0xE620, 2), (0xE621, 0), (0xE620, 4)])

    def test_padding_does_not_hide_later_writer(self):
        result = self.scan("22 00 90 e6 20 74 04 00 00 00 f0 22")
        self.assertEqual(result['writes'][0]['offset'], 10)

    def test_unknown_accumulator_is_not_assumed_zero(self):
        result = self.scan("90 e6 20 f0 22")
        self.assertIsNone(result['writes'][0]['value'])

    def test_increment_dptr_and_accumulator(self):
        result = self.scan("90 e6 20 74 ff 04 f0 a3 14 f0 22")
        self.assertEqual([(w['address'], w['value']) for w in result['writes']],
                         [(0xE620, 0), (0xE621, 255)])

    def test_branch_is_a_boundary_not_linear_proof(self):
        result = self.scan("90 e6 20 74 04 80 00 f0 22")
        self.assertEqual(result['writes'], [])
        self.assertEqual(len(result['boundaries']), 1)

    def test_accumulator_clobber_is_a_boundary(self):
        self.assertEqual(self.scan("90 e6 20 e0 f0 22")['writes'], [])

    def test_new_dptr_stops_old_target(self):
        self.assertEqual(self.scan("90 e6 20 74 04 90 e6 22 f0 22")['writes'], [])

    def test_big_endian_literal_only(self):
        self.assertEqual(self.scan("90 20 e6 74 04 f0 22")['writes'], [])

    def test_increment_past_register_pair_is_not_a_target_write(self):
        result = self.scan("90 e6 21 e4 f0 a3 74 04 f0 22")
        self.assertEqual([(w['address'], w['value']) for w in result['writes']], [(0xE621, 0)])

    def test_hash_rejected_before_paths_are_executed(self):
        with self.assertRaisesRegex(ValueError, 'length'):
            analyze_autoinlen(bytes.fromhex('90 e6 20 74 04 f0 22'))

    def test_zero_branch_and_carry_do_not_change_accumulator(self):
        for value, expected in ((0, 4), (1, 1)):
            machine = Machine(bytes.fromhex('60 01 22 74 04 d3 22'), 0)
            machine.acc = value
            machine.run()
            self.assertEqual(machine.acc, expected)
            self.assertEqual(machine.carry, int(value == 0))

    def test_clear_carry(self):
        machine = Machine(bytes.fromhex('c3 22'), 0)
        machine.carry = 1
        machine.run()
        self.assertEqual(machine.carry, 0)

    def test_renum_bit_uses_accumulator_bit_one(self):
        code = bytes.fromhex('90 e6 80 e0 20 e1 02 d2 09 22')
        for value in (0, 1, 2, 3):
            machine = Machine(code, 0, {0xE680: value})
            machine.run()
            self.assertEqual(machine.bit(9), int(not (value & 2)))


if __name__ == '__main__':
    unittest.main()
