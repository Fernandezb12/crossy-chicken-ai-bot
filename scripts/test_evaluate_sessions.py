import json, subprocess, sys, tempfile, unittest
from pathlib import Path
class EvaluationTest(unittest.TestCase):
 def run_summary(self, rows):
  with tempfile.TemporaryDirectory() as d:
   f=Path(d)/'sessions.jsonl';f.write_text(''.join(json.dumps(r)+'\n' for r in rows))
   return json.loads(subprocess.check_output([sys.executable,str(Path(__file__).with_name('evaluate_sessions.py')),str(f)]))
 def test_missing_scores_are_not_zero(self):
  r=self.run_summary([{'score':10,'survivalMs':1000},{'score':None,'survivalMs':3000},{'score':20,'survivalMs':2000}])
  self.assertEqual(r['completed_sessions'],3);self.assertEqual(r['sessions_with_valid_score'],2)
  self.assertEqual(r['mean'],15);self.assertEqual(r['median'],15);self.assertEqual(r['mean_survival_seconds'],2)
 def test_no_sessions_reports_unknown(self):
  r=self.run_summary([]);self.assertIsNone(r['maximum']);self.assertIsNone(r['mean']);self.assertEqual(r['completed_sessions'],0)
if __name__=='__main__':unittest.main()
