# menu-fillova

Parses a rather weird HTML page of meal menu from **Mateřská škola, Praha 4, Fillova** and creates a Kindle 3 Wifi-friendly PNG image out of it.

Spawns a clojure http server on port 8080. Any incoming request to that URL re-downloads and reproceses the file. No caching.

## Building

```bash
clojure -T:build uber
docker build -t menu-fillova .  
```

## Running

```bash
docker run --rm -p 8080:8080 menu-fillova
```

## Thanks to

- [PeZ's magick-pango-babashka "POC example"](https://github.com/PEZ/magick-pango-babashka)

