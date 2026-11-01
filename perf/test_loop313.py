import io
import unittest
from loop313 import events, percentile


class MeasurementTest(unittest.TestCase):
    def test_receives_complete_frames_with_monotonic_clock(self):
        stream = io.BytesIO(b': ping\r\nevent: youtube-sync.accepted\r\ndata: first\r\ndata: second\r\n\r\n'
                            b'event: youtube-sync.success\ndata: done\n\n')
        ticks = iter([100, 3100])
        self.assertEqual(list(events(stream, lambda: next(ticks))), [
            ('youtube-sync.accepted', 'first\nsecond', 100),
            ('youtube-sync.success', 'done', 3100)])

    def test_does_not_emit_unterminated_frame(self):
        self.assertEqual(list(events(io.BytesIO(b'event: youtube-sync.success\n'))), [])

    def test_percentile_interpolates_small_sample(self):
        self.assertEqual(percentile([40, 10, 30, 20], .5), 25)
        self.assertAlmostEqual(percentile([40, 10, 30, 20], .95), 38.5)


if __name__ == '__main__':
    unittest.main()
