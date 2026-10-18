#!/usr/bin/env python3
"""
Synthesize a light, upbeat "office / corporate" background track with the Python standard library only.

No samples, no copyright: a bright C-major I-V-vi-IV progression, a bouncy marimba-style plucked
arpeggio, a soft bass on the beat, and a quiet shaker for a light groove. Writes a WAV that loops
cleanly; the build script lowers its volume and loops it to each video's length. Generated once and
reused, so this runs only when assets/bgm.wav is missing (or --force).

Usage: python3 make_bgm.py <out.wav> [--force]
"""
import math
import os
import random
import struct
import sys
import wave

SR = 44100
random.seed(7)

N = {  # note name -> frequency (Hz)
    'C2': 65.41, 'E2': 82.41, 'F2': 87.31, 'G2': 98.00, 'A2': 110.00,
    'C3': 130.81, 'D3': 146.83, 'E3': 164.81, 'F3': 174.61, 'G3': 196.00, 'A3': 220.00, 'B3': 246.94,
    'C4': 261.63, 'D4': 293.66, 'E4': 329.63, 'F4': 349.23, 'G4': 392.00, 'A4': 440.00, 'B4': 493.88,
    'C5': 523.25, 'D5': 587.33, 'E5': 659.25, 'F5': 698.46, 'G5': 783.99, 'A5': 880.00,
}

# One chord per bar: bass root, and the four arpeggio tones (a triad spread up an octave).
BARS = [
    ('C3', ['C4', 'E4', 'G4', 'C5']),   # C  (I)
    ('G2', ['B3', 'D4', 'G4', 'B4']),   # G  (V)
    ('A2', ['A3', 'C4', 'E4', 'A4']),   # Am (vi)
    ('F2', ['A3', 'C4', 'F4', 'A4']),   # F  (IV)
    ('C3', ['C4', 'E4', 'G4', 'C5']),   # C
    ('G2', ['B3', 'D4', 'G4', 'B4']),   # G
    ('F2', ['A3', 'C4', 'F4', 'A4']),   # F
    ('G2', ['B3', 'D4', 'G4', 'D5']),   # G -> turnaround back to C
]
# A sparse, cheerful top melody (bar index -> list of (note, eighth-offset, eighths-long)).
MELODY = {
    0: [('E5', 0, 2), ('G5', 2, 2), ('C5', 4, 2), ('E5', 6, 2)],
    2: [('A4', 0, 2), ('C5', 2, 2), ('E5', 4, 4)],
    4: [('G4', 0, 2), ('C5', 2, 2), ('E5', 4, 2), ('G5', 6, 2)],
    6: [('A4', 0, 2), ('C5', 2, 2), ('D5', 4, 4)],
}

EIGHTH = 0.205                 # seconds per eighth note (brisk, ~146 bpm quarter)
BAR = 8 * EIGHTH
# Bouncy up-and-down arpeggio pattern over the four tones (index into the bar's tone list).
ARP = [0, 1, 2, 3, 2, 1, 2, 3]


def pluck(freq, dur, amps, decay):
    """A short marimba-ish pluck: fundamental plus a couple of partials, fast attack, quick decay."""
    n = int(dur * SR)
    out = [0.0] * n
    for h, a in amps:
        w = 2 * math.pi * freq * h
        for i in range(n):
            out[i] += a * math.sin(w * (i / SR))
    atk = max(1, int(0.004 * SR))
    for i in range(n):
        env = (i / atk) if i < atk else math.exp(-decay * (i - atk) / SR)
        out[i] *= env
    return out


def shaker(dur):
    """A quiet high noise tick for a light groove."""
    n = int(dur * SR)
    out = [0.0] * n
    prev = 0.0
    for i in range(n):
        w = random.uniform(-1, 1)
        hp = w - prev           # crude high-pass so it sits high and soft
        prev = w
        out[i] = hp * math.exp(-60.0 * i / SR)
    return out


def add(buf, samples, start, gain):
    for i, s in enumerate(samples):
        j = start + i
        if 0 <= j < len(buf):
            buf[j] += gain * s


def main():
    if len(sys.argv) < 2:
        print('usage: make_bgm.py <out.wav> [--force]', file=sys.stderr)
        sys.exit(2)
    out_path = sys.argv[1]
    if os.path.exists(out_path) and '--force' not in sys.argv:
        print(f'bgm exists, keeping {out_path}')
        return

    length = len(BARS) * BAR + 0.6
    buf = [0.0] * int(length * SR)

    for b, (bass, tones) in enumerate(BARS):
        bar_t = b * BAR
        # Bass on beats 1 and 3 (eighth 0 and 4).
        for off in (0, 4):
            add(buf, pluck(N[bass], 2 * EIGHTH + 0.1, [(1, 0.6), (2, 0.12)], 6.0),
                int((bar_t + off * EIGHTH) * SR), 0.5)
        # Marimba arpeggio, one pluck per eighth.
        for e in range(8):
            tone = tones[ARP[e]]
            add(buf, pluck(N[tone], EIGHTH + 0.14, [(1, 0.5), (4, 0.16), (10, 0.04)], 14.0),
                int((bar_t + e * EIGHTH) * SR), 0.34)
        # Shaker on the offbeats.
        for e in (1, 3, 5, 7):
            add(buf, shaker(0.06), int((bar_t + e * EIGHTH) * SR), 0.09)
        # Melody, where present.
        for note, off, ln in MELODY.get(b, []):
            add(buf, pluck(N[note], ln * EIGHTH + 0.12, [(1, 0.5), (2, 0.14), (3, 0.05)], 7.0),
                int((bar_t + off * EIGHTH) * SR), 0.3)

    peak = max(1e-6, max(abs(x) for x in buf))
    gain = 0.86 / peak
    delay = int(0.008 * SR)     # tiny stereo width
    frames = bytearray()
    for i in range(len(buf)):
        left = buf[i] * gain
        ri = i - delay
        right = (buf[ri] if ri >= 0 else 0.0) * gain
        frames += struct.pack('<hh',
                              int(max(-1, min(1, left)) * 32767),
                              int(max(-1, min(1, right)) * 32767))

    with wave.open(out_path, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(bytes(frames))
    print(f'wrote {out_path}  ({length:.1f}s, stereo {SR} Hz)')


if __name__ == '__main__':
    main()
