#!/usr/bin/env python3
"""Synthesises the sounds of the "Hyrule Dawn" theme: an ocarina-like flute and a plucked harp.

The flute is a sine with a touch of second harmonic, a soft breath attack and a gentle vibrato; the
harp reuses the plucked-string model of the Sakura Night script. The little melodies are original
(none is taken from any game). Output: 16-bit mono WAV files in the theme's sounds folder.
Usage: python3 tools/themes/make_hyrule_dawn_sounds.py   (needs numpy)
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_sakura_night_sounds as base  # noqa: E402

base.OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "themes", "hyrule-dawn", "sounds")
SR = base.SR

# D major pentatonic plus the fourth, which gives the open, pastoral feel.
N = {"D4": 293.66, "E4": 329.63, "F#4": 369.99, "G4": 392.0, "A4": 440.0, "B4": 493.88, "D5": 587.33, "E5": 659.25,
     "F#5": 739.99, "G5": 783.99, "A5": 880.0, "B5": 987.77, "D6": 1174.66, "A3": 220.0, "D3": 146.83, "E3": 164.81}


def flute(freq, dur, vib=5.2, vib_depth=0.006, breath=0.05, seed=0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    env = np.minimum(1, t / 0.045) * np.exp(-1.4 * np.maximum(0, t - dur * 0.55) / dur * 4) * np.minimum(1, (dur - t) / 0.06)
    f = freq * (1 + vib_depth * np.sin(2 * np.pi * vib * t) * np.minimum(1, t / 0.25))
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.18 * np.sin(2 * phase) + 0.05 * np.sin(3 * phase)
    noise = np.random.default_rng(seed).uniform(-1, 1, n)
    noise = np.convolve(noise, np.ones(6) / 6, mode="same")
    return (tone + breath * noise * np.exp(-6 * t)) * env


def harp(freq, dur, seed=1):
    return base.pluck(freq, dur, decay=0.9985, bright=0.35, seed=seed)


def shimmer(freq, dur, amp=0.25):
    return base.bell(freq, dur, partials=((1, 1.0), (2.0, 0.5), (3.01, 0.25)), decay=4.0) * amp


def track(seconds):
    return np.zeros(int(SR * seconds))


def main():
    t = track(0.5); base.place(t, flute(N["D5"], 0.22), 0.0, 0.8); base.write("move", base.finish(t, tail=0.1, echo=(0.13, 0.3), peak=0.5))

    t = track(0.7)
    base.place(t, harp(N["D5"], 0.4, 2), 0.0); base.place(t, harp(N["A5"], 0.5, 3), 0.07, 0.9); base.place(t, shimmer(N["D6"], 0.5), 0.07)
    base.write("select", base.finish(t, tail=0.2, echo=(0.14, 0.3), peak=0.7))

    t = track(0.6)
    base.place(t, flute(N["A4"], 0.2), 0.0, 0.7); base.place(t, flute(N["E4"], 0.3), 0.12, 0.7)
    base.write("back", base.finish(t, tail=0.15, echo=(0.14, 0.3), peak=0.6))

    t = track(0.5)
    base.place(t, base.whoosh(0.2, 800, 3500), 0.0, 0.4); base.place(t, harp(N["F#5"], 0.3, 6), 0.08, 0.8)
    base.write("tab", base.finish(t, tail=0.1, peak=0.55))

    t = track(1.1)
    for i, n in enumerate(("D5", "F#5", "A5", "D6")):
        base.place(t, harp(N[n], 0.6, 10 + i), i * 0.08, 0.8); base.place(t, shimmer(N[n] * 2 if n != "D6" else N[n], 0.6, 0.18), i * 0.08)
    base.write("favorite", base.finish(t, tail=0.3, echo=(0.15, 0.32), peak=0.7))

    t = track(0.7)
    base.place(t, harp(N["D4"], 0.5, 20), 0.0); base.place(t, flute(N["A5"], 0.35), 0.05, 0.3)
    base.write("menu", base.finish(t, tail=0.15, peak=0.55))

    t = track(1.6)
    base.place(t, base.sweep(300, 1400, 0.6, 0.3), 0.0)
    for i, n in enumerate(("D5", "A5", "B5", "D6")):
        base.place(t, harp(N[n], 0.7, 30 + i), 0.4 + i * 0.07, 0.8)
    base.place(t, flute(N["D6"], 0.7), 0.7, 0.35)
    base.write("launch", base.finish(t, tail=0.5, echo=(0.16, 0.34), peak=0.75))

    t = track(3.2)
    for i, (n, at) in enumerate((("D4", 0.0), ("A4", 0.25), ("D5", 0.5), ("F#5", 0.75))):
        base.place(t, harp(N[n], 1.2, 40 + i), at, 0.7)
    base.place(t, flute(N["A5"], 0.9), 1.05, 0.55); base.place(t, flute(N["B5"], 0.5), 1.9, 0.5); base.place(t, flute(N["D6"], 1.1), 2.3, 0.5)
    base.place(t, shimmer(N["D6"], 1.0), 2.3)
    base.write("startup", base.finish(t, tail=0.8, echo=(0.2, 0.36), peak=0.75))

    t = track(1.1)
    base.place(t, shimmer(N["A5"], 0.9, 0.9), 0.0); base.place(t, shimmer(N["D6"], 0.9, 0.8), 0.14); base.place(t, harp(N["D5"], 0.6, 50), 0.0, 0.5)
    base.write("done", base.finish(t, tail=0.3, peak=0.7))

    t = track(0.7)
    base.place(t, flute(N["E3"] * 2, 0.3, vib_depth=0.012), 0.0, 0.8); base.place(t, flute(N["D3"] * 2, 0.4, vib_depth=0.012), 0.16, 0.8)
    base.write("error", base.finish(t, tail=0.1, peak=0.6))


if __name__ == "__main__":
    main()
