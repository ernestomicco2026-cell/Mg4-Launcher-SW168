"""Genera le icone vettoriali (VectorDrawable) del launcher in app/res/drawable.

Icone lineari 24x24 in stile "Tesla": tratto 1.8, bianco, colorate a runtime con setColorFilter.
"""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "app/res/drawable"

STROKE = {
    "car": "M5 17h14M3 13l2-6h14l2 6v5H3zM7 17v2M17 17v2M6.5 13h0.01M17.5 13h0.01",
    "fan": "M12 12c0-3 2.5-6 6-6 0 3.5-3 6-6 6zM12 12c3 0 6 2.5 6 6-3.5 0-6-3-6-6zM12 12c0 3-2.5 6-6 6 0-3.5 3-6 6-6zM12 12c-3 0-6-2.5-6-6 3.5 0 6 3 6 6z",
    "bolt": "M13 2 4 14h7l-1 8 9-12h-7z",
    "chart": "M3 3v18h18M7 15l4-4 3 3 5-6",
    "phone": "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c0.1 1 0.4 1.9 0.7 2.8a2 2 0 0 1-0.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-0.4c0.9 0.3 1.8 0.6 2.8 0.7a2 2 0 0 1 1.7 2z",
    "radio": "M2 8h20v13H2zM6 8l10-5M5 12h5M5 16h5M16 12a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z",
    "music": "M9 18V5l12-2v13M6 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM18 13a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
    "video": "M2 6h14v12H2zM16 10l6-3v10l-6-3",
    "cam": "M3 7h3l2-3h8l2 3h3v12H3zM12 9.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z",
    "gear": "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1",
    "book": "M4 4h6a3 3 0 0 1 3 3v13a2 2 0 0 0-2-2H4zM20 4h-6a3 3 0 0 0-3 3v13a2 2 0 0 1 2-2h7z",
    "route": "M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM6 15V9a4 4 0 0 1 4-4h6M18 9v6a4 4 0 0 1-4 4H8",
    "wave": "M4 10c3-2 6-2 8 0s5 2 8 0M4 14c3-2 6-2 8 0s5 2 8 0",
    "cloud": "M7 18h10a4 4 0 0 0 0.5-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18z",
    "cast": "M2 16a6 6 0 0 1 6 6M2 12a10 10 0 0 1 10 10M2 20h0.01M4 4h16v14h-6",
    "tasks": "M4 4h16v16H4zM8 9h8M8 13h8M8 17h5",
    "user": "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0",
    "sos": "M12 2 3 6v6c0 5 4 9 9 10 5-1 9-5 9-10V6zM12 8v5M12 16h0.01",
    "grid": "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z",
    "chev_left": "M15 18l-6-6 6-6",
    "chev_right": "M9 18l6-6-6-6",
    "turn": "M7 20V11a3 3 0 0 1 3-3h9M15 4l4 4-4 4",
    "home": "M3 11l9-8 9 8M5 9v12h14V9M10 21v-6h4v6",
    "work": "M3 7h18v13H3zM8 7V4h8v3M3 12h18",
    "smartphone": "M6 2h12v20H6zM11 18h2",
    "usb": "M7 3h10v7H7zM5 10h14v11H5zM10 6h1M13 6h1",
    "bluetooth": "M7 7l10 10-5 5V2l5 5L7 17",
    "nav": "M12 2 4 21l8-4 8 4z",
    "globe": "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18",
    "app": "M5 5h14v14H5zM9 9h6v6H9z",
    "power": "M12 2v10M18.4 6.6a9 9 0 1 1-12.8 0",
    "palette": "M12 3a9 9 0 1 0 0 18c1.1 0 1.6-0.8 1.6-1.6 0-0.9-0.7-1.4-0.7-2.3 0-1 0.8-1.6 1.8-1.6H17a4 4 0 0 0 4-4c0-4.7-4-8.5-9-8.5zM7.5 11a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM10.5 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM15 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
    "info": "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5h0.01",
    "close": "M6 6l12 12M18 6L6 18",
    "plug": "M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0zM12 18v4",
}

FILL = {
    "play": "M8 5v14l11-7z",
    "pause": "M7 5h4v14H7zM13 5h4v14h-4z",
    "prev": "M6 5h2v14H6zM20 5v14L9 12z",
    "next": "M16 5h2v14h-2zM4 5v14l11-7z",
}

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="{d}" {paint}/>
</vector>
"""

os.makedirs(OUT, exist_ok=True)
for name, d in STROKE.items():
    paint = ('android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" '
             'android:strokeLineCap="round" android:strokeLineJoin="round"')
    open(os.path.join(OUT, "ic_" + name + ".xml"), "w").write(TEMPLATE.format(d=d, paint=paint))
for name, d in FILL.items():
    open(os.path.join(OUT, "ic_" + name + ".xml"), "w").write(TEMPLATE.format(d=d, paint='android:fillColor="#FFFFFFFF"'))
print(len(STROKE) + len(FILL), "icone in", OUT)
