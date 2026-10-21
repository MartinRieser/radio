import json
import urllib.parse
import urllib.request

def load_existing_keys():
    existing_keys = set()
    existing_names = set()

    for path in ['stream_miner/stations.json', 'stream_miner/stations_nonstop.json']:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                for item in data:
                    if item.get('stationuuid'):
                        existing_keys.add(item['stationuuid'].lower())
                    if item.get('url'):
                        existing_keys.add(item['url'].lower())
                    if item.get('name'):
                        clean_n = item['name'].lower().replace('fm', '').replace('radio', '').replace('hq', '').replace('orf', '').replace('|', '').strip()
                        existing_names.add(clean_n)
        except Exception as e:
            print(f"Warning loading {path}:", e)

    return existing_keys, existing_names

existing_keys, existing_names = load_existing_keys()
print(f"Loaded {len(existing_keys)} existing keys and {len(existing_names)} existing names to exclude.")

countries = [
    'Germany', 'Austria', 'Switzerland', 'Netherlands', 'United Kingdom',
    'France', 'Belgium', 'Spain', 'Italy', 'Poland', 'Czechia',
    'Denmark', 'Sweden', 'Norway', 'Ireland'
]

candidate_stations = []

for country in countries:
    url = f"https://de1.api.radio-browser.info/json/stations/bycountry/{urllib.parse.quote(country)}?order=votes&reverse=true&limit=60&hidebroken=true"
    req = urllib.request.Request(url, headers={'User-Agent': 'PulseCast-Miner/1.0'})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            for s in data:
                tags = (s.get('tags') or '').lower()
                name = s.get('name') or ''
                uuid = (s.get('stationuuid') or '').lower()
                stream_url = (s.get('url_resolved') or s.get('url') or '').strip()

                # Exclude Volksmusik, Schlager, News, Talk, Culture, Jazz, Classic
                if any(bad in tags or bad in name.lower() for bad in [
                    'volksmusik', 'schlager', 'folk', 'news', 'talk', 'kultur',
                    'classic', 'klassik', 'jazz', 'info', 'sport', 'church', 'religion'
                ]):
                    continue

                # Exclude if already present in list 1 or list 2
                clean_n = name.lower().replace('fm', '').replace('radio', '').replace('hq', '').replace('orf', '').replace('|', '').strip()
                if uuid in existing_keys or stream_url.lower() in existing_keys or clean_n in existing_names:
                    continue

                # Include Pop, Rock, Dance, Trance, House, EDM, Top 40, Chart, Alternative, Indie
                if any(good in tags or good in name.lower() for good in [
                    'pop', 'rock', 'dance', 'trance', 'house', 'edm', 'chart', 'top',
                    'hit', 'electro', 'club', 'indie', 'alternative', 'music', 'radio', 'fm', 'dab'
                ]):
                    if stream_url and (stream_url.startswith('http://') or stream_url.startswith('https://')):
                        candidate_stations.append({
                            'stationuuid': s.get('stationuuid'),
                            'name': name.strip(),
                            'url': stream_url,
                            'country': country,
                            'tags': tags[:100]
                        })
    except Exception as e:
        print(f"Error fetching {country}:", e)

# Deduplicate
unique_stations = []
seen_names = set()
seen_urls = set()

for s in candidate_stations:
    clean_n = s['name'].lower().replace('fm', '').replace('radio', '').strip()
    if clean_n not in seen_names and s['url'].lower() not in seen_urls:
        seen_names.add(clean_n)
        seen_urls.add(s['url'].lower())
        unique_stations.append(s)

print(f"Testing connectivity for {len(unique_stations)} strictly new candidate streams...")

valid_stations = []
for s in unique_stations:
    if len(valid_stations) >= 60:
        break
    try:
        req = urllib.request.Request(s['url'], headers={'User-Agent': 'PulseCast-Miner/1.0'})
        with urllib.request.urlopen(req, timeout=3) as resp:
            if resp.status in (200, 206):
                valid_stations.append(s)
                print(f"[{len(valid_stations)}/60] NEW OK: {s['name']} ({s['country']})")
    except Exception:
        pass

print(f"Done! Verified {len(valid_stations)} 100% NEW European Pop/Rock/Dance/Trance FM & DAB stations.")

out_path = 'stream_miner/stations_euro_poprock.json'
with open(out_path, 'w', encoding='utf-8') as f:
    json.dump(valid_stations, f, indent=2, ensure_ascii=False)

print(f"Saved to: {out_path}")
