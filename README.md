# 🐾 Did They Eat Today? — Pet Feeding Tracker

A simple, cute, mobile-first web app to track feedings for up to **4 pets** with a single tap. No build step, no dependencies — just open `index.html` in any browser (phone or desktop). All data is saved locally on the device via `localStorage`, so it persists after you close and reopen the app.

## Features

- **2×2 grid of pet cards** (stacks to one column on narrow screens)
- **Editable pet names** — tap a name to rename (defaults to Pet 1–4)
- **Large Feed button** per card with a confirmation prompt to prevent accidental taps
- **Last fed status** shown as `Last fed: 2026.10.01 14:20`
- **"Fed Today" visual feedback** — card turns soft mint green with a ✓ badge
- **View History** — slide-up modal listing all feeding timestamps per pet (newest first)
- **Warm pastel design** — clean, minimal, large tap targets

## Usage

Open `index.html` in a browser. On a phone, you can also "Add to Home Screen" for an app-like experience.

## Tech

- Single-file vanilla HTML/CSS/JavaScript
- Persistence via browser `localStorage` (key: `petFeedingTracker.v1`)
- Zero external dependencies, works fully offline
