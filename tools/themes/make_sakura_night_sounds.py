#!/usr/bin/env python3
"""Synthesises the sounds of the "Sakura Night" theme: plucked strings in a Japanese scale.

A plucked string is modelled with the Karplus-Strong algorithm (a short burst of noise circulating
in a delay line that loses a little energy each turn), which sounds like a koto or a harp. A small
echo gives each sound some air. Output: 16-bit mono WAV files, no third-party audio.

Usage: python3 tools/themes/make_sakura_night_sounds.py
Needs numpy.
"""
import os
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "themes", "sakura-night", "sounds")

# Miyako-bushi scale on E: E F A B C. Frequencies in Hz.
NOTE = {
    "E3": 164.81, "F3": 174.61, "A3": 220.00, "B3": 246.94, "C4": 261.63,
    "E4": 329.63, "F4": 349.23, "A4": 440.00, "B4": 493.88, "C5": 523.25,
    "E5": 659.25, "F5": 698.46, "A5": 880.00, "B5": 987.77, "C6": 1046.50,
    "E6": 1318.51, "F6": 1396.91, "A6": 1760.00, "B6": 1975.53, "C7": 2093.00,
}


def pluck(freq, dur, decay=0.996, bright=0.5, seed=1):
    """Karplus-Strong string: noise burst through a delay line with a gentle low-pass."""
    n = int(SR * dur)
    period = SR / freq
    size = int(period)
    frac = period - size
    rnd = np.random.default_rng(seed)
    buf = rnd.uniform(-1, 1, size + 1)
    # a softer attack: smooth the initial burst
    for _ in range(2):
        buf = (buf + np.roll(buf, 1)) / 2
    out = np.zeros(n)
    idx = 0
    prev = 0.0
    for i in range(n):
        a = buf[idx % (size + 1)]
        b = buf[(idx + 1) % (size + 1)]
        new = decay * ((1 - bright) * a + bright * (a + b) / 2)
        out[i] = a
        buf[idx % (size + 1)] = new
        idx += 1
    return out


def bell(freq, dur, partials=((1, 1.0), (2.76, 0.45), (5.4, 0.2)), decay=5.5):
    t = np.arange(int(SR * dur)) / SR
    sig = np.zeros_like(t)
    for ratio, amp in partials:
        sig += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-decay * (1 + 0.4 * ratio) * t)
    return sig


def sweep(f0, f1, dur, amp=0.4):
    t = np.arange(int(SR * dur)) / SR
    f = f0 + (f1 - f0) * (t / dur) ** 2
    phase = 2 * np.pi * np.cumsum(f) / SR
    env = np.sin(np.pi * t / dur) ** 1.5
    return amp * np.sin(phase) * env


def whoosh(dur, lo, hi, amp=0.25):
    rnd = np.random.default_rng(5)
    noise = rnd.uniform(-1, 1, int(SR * dur))
    t = np.arange(len(noise)) / SR
    # band-limited by mixing a few lightly filtered copies, with a rising centre
    out = np.zeros_like(noise)
    k = int(SR / (lo + (hi - lo) * 0.5) / 2) or 1
    smooth = np.convolve(noise, np.ones(k) / k, mode="same")
    out = smooth - np.convolve(smooth, np.ones(k * 6) / (k * 6), mode="same")
    return amp * out / (np.max(np.abs(out)) + 1e-9) * np.sin(np.pi * t / dur) ** 2


def place(track, sig, start, gain=1.0):
    s = int(SR * start)
    end = min(len(track), s + len(sig))
    track[s:end] += gain * sig[: end - s]


def finish(sig, tail=0.0, echo=(0.11, 0.28), peak=0.8):
    """Adds a soft echo, fades the end out so nothing clicks, and normalises."""
    n = len(sig) + int(SR * tail)
    out = np.zeros(n)
    out[: len(sig)] = sig
    delay = int(SR * echo[0])
    dry = out.copy()
    for k in (1, 2, 3):
        shift = delay * k
        if shift < n:
            out[shift:] += (echo[1] ** k) * dry[: n - shift]
    fade = int(SR * 0.03)
    out[-fade:] *= np.linspace(1, 0, fade)
    out[:64] *= np.linspace(0, 1, 64)
    out = out / (np.max(np.abs(out)) + 1e-9) * peak
    return out


def write(name, sig):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(sig, -1, 1) * 32767).astype("<i2").tobytes())
    print(name, os.path.getsize(path) // 1024, "KB", round(len(sig) / SR, 2), "s")


def main():
    # move: one short, soft pluck
    write("move", finish(pluck(NOTE["E5"], 0.22, decay=0.992, bright=0.6), tail=0.1, peak=0.55))

    # select: two notes going up with a bell on top
    t = np.zeros(int(SR * 0.6))
    place(t, pluck(NOTE["E5"], 0.3, 0.995), 0.0)
    place(t, pluck(NOTE["A5"], 0.4, 0.996, seed=2), 0.07, 0.9)
    place(t, bell(NOTE["A6"], 0.4), 0.07, 0.25)
    write("select", finish(t, tail=0.15, peak=0.7))

    # back: two notes going down
    t = np.zeros(int(SR * 0.5))
    place(t, pluck(NOTE["A4"], 0.28, 0.994, seed=3), 0.0)
    place(t, pluck(NOTE["E4"], 0.34, 0.995, seed=4), 0.08, 0.9)
    write("back", finish(t, tail=0.1, peak=0.6))

    # tab: a quick breath of air and a high pluck
    t = np.zeros(int(SR * 0.4))
    place(t, whoosh(0.22, 600, 3000), 0.0, 0.5)
    place(t, pluck(NOTE["B5"], 0.25, 0.994, seed=6), 0.09, 0.8)
    write("tab", finish(t, tail=0.1, peak=0.6))

    # favorite: a sparkling run up the scale
    t = np.zeros(int(SR * 1.0))
    for i, n in enumerate(("E6", "F6", "A6", "B6", "C7")):
        place(t, bell(NOTE[n], 0.5, decay=6.5), i * 0.065, 0.55)
        place(t, pluck(NOTE[n] / 2, 0.3, 0.993, seed=10 + i), i * 0.065, 0.4)
    write("favorite", finish(t, tail=0.25, peak=0.7))

    # menu: a low pluck with a shimmer
    t = np.zeros(int(SR * 0.6))
    place(t, pluck(NOTE["E4"], 0.45, 0.997, seed=7), 0.0)
    place(t, bell(NOTE["B5"], 0.4, decay=7), 0.04, 0.12)
    write("menu", finish(t, tail=0.15, peak=0.6))

    # launch: a rising sweep over a bright chord, then a bell
    t = np.zeros(int(SR * 1.2))
    place(t, sweep(280, 1500, 0.7, 0.35), 0.0)
    for i, n in enumerate(("E5", "A5", "B5", "E6")):
        place(t, pluck(NOTE[n], 0.5, 0.996, seed=20 + i), 0.45 + i * 0.03, 0.6)
    place(t, bell(NOTE["E6"], 0.6, decay=4.5), 0.5, 0.3)
    write("launch", finish(t, tail=0.4, peak=0.75))

    # startup: a short koto phrase
    t = np.zeros(int(SR * 2.4))
    phrase = [("E4", 0.0), ("A4", 0.18), ("B4", 0.36), ("C5", 0.54), ("E5", 0.78), ("A5", 1.02), ("E5", 1.3), ("A4", 1.5)]
    for i, (n, at) in enumerate(phrase):
        place(t, pluck(NOTE[n], 0.9, 0.9975, seed=30 + i), at, 0.7)
    place(t, bell(NOTE["E6"], 1.0, decay=3.5), 1.5, 0.22)
    write("startup", finish(t, tail=0.6, echo=(0.16, 0.34), peak=0.75))

    # done: a clear two-note chime
    t = np.zeros(int(SR * 1.0))
    place(t, bell(NOTE["C6"], 0.9, decay=3.2), 0.0, 0.7)
    place(t, bell(NOTE["E6"], 0.9, decay=3.2), 0.12, 0.6)
    write("done", finish(t, tail=0.3, peak=0.7))

    # error: two low, dull plucks a semitone apart
    t = np.zeros(int(SR * 0.6))
    place(t, pluck(NOTE["E3"], 0.35, 0.99, bright=0.8, seed=40), 0.0)
    place(t, pluck(NOTE["F3"], 0.4, 0.99, bright=0.8, seed=41), 0.12, 0.9)
    write("error", finish(t, tail=0.1, peak=0.65))


if __name__ == "__main__":
    main()
