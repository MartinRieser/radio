import json
import urllib.parse
import urllib.request

countries = [
    'Germany', 'Austria', 'Switzerland', 'Netherlands', 'United Kingdom',
    'France', 'Belgium', 'Spain', 'Italy', 'Poland', 'Czechia',
    'Denmark', 'Sweden', 'Norway', 'Ireland'
]

all_stations = []

for country in countries:
    url = f"https://de1.api.radio-browser.info/json/stations/bycountry/{urllib.parse.quote(country)}?order=votes&reverse=true&limit=40&hidebroken=true"
    req = urllib.request.Request(url, headers={'User-Agent': 'PulseCast-Miner/1.0'})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            for s in data:
                tags = (s.get('tags') or '').lower()
                name = s.get('name') or ''
                # Exclude Volksmusik, Schlager, News, Talk, Culture, Jazz, Classic
                if any(bad in tags or bad in name.lower() for bad in [
                    'volksmusik', 'schlager', 'folk', 'news', 'talk', 'kultur',
                    'classic', 'klassik', 'jazz', 'info', 'sport', 'church', 'religion'
                ]):
                    continue
                # Include Pop, Rock, Dance, Trance, House, EDM, Top 40, Chart, Alternative, Indie
                if any(good in tags or good in name.lower() for good in [
                    'pop', 'rock', 'dance', 'trance', 'house', 'edm', 'chart', 'top',
                    'hit', 'electro', 'club', 'indie', 'alternative', 'music', 'radio', 'fm', 'dab'
                ]):
                    stream_url = s.get('url_resolved') or s.get('url')
                    if stream_url and (stream_url.startswith('http://') or stream_url.startswith('https://')):
                        all_stations.append({
                            'stationuuid': s.get('stationuuid'),
                            'name': name.strip(),
                            'url': stream_url.strip(),
                            'country': country,
                            'tags': tags[:100]
                        })
    except Exception as e:
        print(f"Error fetching {country}:", e)

# Deduplicate
unique_stations = []
seen_names = set()
seen_urls = set()

for s in all_stations:
    clean_name = s['name'].lower().replace('fm', '').replace('radio', '').strip()
    if clean_name not in seen_names and s['url'] not in seen_urls:
        seen_names.add(clean_name)
        seen_urls.add(s['url'])
        unique_stations.append(s)

print(f"Testing connectivity for {len(unique_stations)} candidate streams...")

valid_stations = []
for s in unique_stations:
    if len(valid_stations) >= 60:
        break
    try:
        req = urllib.request.Request(s['url'], headers={'User-Agent': 'PulseCast-Miner/1.0'})
        with urllib.request.urlopen(req, timeout=3) as resp:
            if resp.status in (200, 206):
                valid_stations.append(s)
                print(f"[{len(valid_stations)}/60] OK: {s['name']} ({s['country']})")
    except Exception:
        pass

print(f"Done! Verified {len(valid_stations)} European Pop/Rock/Dance/Trance FM & DAB stations.")

out_path = 'stream_miner/stations_euro_poprock.json'
with open(out_path, 'w', encoding='utf-8') as f:
    json.dump(valid_stations, f, indent=2, ensure_ascii=False)

print(f"Saved to: {out_path}")
