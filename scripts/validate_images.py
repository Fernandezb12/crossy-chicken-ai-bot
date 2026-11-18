#!/usr/bin/env python3
"""Host-only static image evaluation. Not an Android or autonomous gameplay test.
Requires: pip install opencv-python-headless
Training: playing-base and playing-back. The other five images are held out.
"""
from pathlib import Path
import cv2,json
root=Path(__file__).resolve().parents[1]/'app/src/androidTest/assets'
def read(name):
 image=cv2.imread(str(root/(name+'.png')))
 if image is None:raise ValueError(name)
 return image
samples=[read('playing-base')[451:526,89:153],read('playing-back')[323:393,200:259]]
expected={'playing-right':(159,467),'playing-left':(201,421),'playing-up':(250.5,360.1),'mobile-idle':None,'bottom':None}
for name,truth in expected.items():
 image=read(name);candidates=[]
 for template in samples:
  res=cv2.matchTemplate(image,template,cv2.TM_SQDIFF_NORMED)
  low,_,(x,y),_=cv2.minMaxLoc(res);h,w=template.shape[:2]
  # Same exclusion size and criterion as the Android detector.
  l=max(0,x-w//2);t=max(0,y-h//2);res[t:min(t+h,res.shape[0]),l:min(l+w,res.shape[1])]=1
  gap=cv2.minMaxLoc(res)[0]-low
  if 1-low>=.86 and gap>=.018:candidates.append((1-low,gap,(x+w/2,y+h*.83)))
 best=max(candidates,default=None,key=lambda v:v[0])
 correct=best is None if truth is None else best is not None and ((best[2][0]-truth[0])**2+(best[2][1]-truth[1])**2)**.5<15
 result={'frame':name,'truth':truth,'confidence':best[0] if best else None,'detectedFoot':best[2] if best else None,'correct':correct}
 print(json.dumps(result))
 assert correct,result
