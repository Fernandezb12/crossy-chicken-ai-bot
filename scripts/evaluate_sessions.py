#!/usr/bin/env python3
"""Summarize exported observations. Missing OCR scores remain unknown."""
import argparse, json, statistics
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('sessions',type=Path);p.add_argument('--events',type=Path);args=p.parse_args()
def load(f):return [json.loads(line) for line in f.read_text().splitlines() if line.strip()]
rows=load(args.sessions)
scores=[r['score'] for r in rows if isinstance(r.get('score'),int)]
survival=[r['survivalMs']/1000 for r in rows if isinstance(r.get('survivalMs'),(int,float))]
result={'completed_sessions':len(rows),'sessions_with_valid_score':len(scores),
 'maximum':max(scores) if scores else None,'mean':statistics.mean(scores) if scores else None,
 'median':statistics.median(scores) if scores else None,
 'mean_survival_seconds':statistics.mean(survival) if survival else None}
if args.events:
 events=load(args.events);actions=[r for r in events if r.get('type')=='action'];outcomes=[r for r in events if r.get('type')=='result']
 valid=[r for r in outcomes if r.get('accepted') is True]
 result.update({'recorded_actions':len(actions),'recorded_outcomes':len(outcomes),
  'visually_accepted_percent_of_outcomes':100*len(valid)/len(outcomes) if outcomes else None,
  'mean_verified_response_ms':statistics.mean(r['latencyMs'] for r in valid) if valid else None,
  'note':'Log rotation/disabled logging can omit events. Visual acceptance is not ground-truth detection accuracy.'})
print(json.dumps(result,indent=2))
