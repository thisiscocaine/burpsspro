# BurpSS - Professional Screenshot Tool for Burp Suite

**BurpSS** is a high-precision screenshot extension designed specifically for pentesters and bug bounty hunters. It solves the pain point of taking clean, report-ready screenshots of Burp requests, repeater tabs, terminals, and external tools without the need for cropping or manual editing.

![Version](https://img.shields.io/badge/version-3.0.0-blue) ![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)

## 🚀 Key Features

*   **Dual Capture Engine:**
    *   **Internal:** Intelligently captures only the active Repeater/Proxy pane (ignores Burp's sidebar and top menus).
    *   **External:** Captures any active OS window (Terminal, Browser, Postman) with pixel-perfect precision.
*   **Smart Background Removal:** Automatically crops out desktop wallpaper, shadows, and rounded corners (Windows 11 / macOS support).
*   **Cross-Platform:** Native support for Windows (DWM), macOS (AppleScript), and Linux (X11).
*   **Zero-Noise UI:** Minimalist, centered interface designed to stay out of your way.

---

## 🛠️ Build & Install

### Prerequisites
*   Java JDK 11 or higher
*   Maven

### 1. Build the JAR
Open your terminal in the project folder and run:

```bash
mvn clean package
