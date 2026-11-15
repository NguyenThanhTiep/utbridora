import { test, expect } from '@playwright/test';
import { previewTotal, rubricValid, scoreValid, scoresValid, studentEvaluation, type Rubric, type ScoreDraft, type Evaluation } from '../../src/modules/internship/evaluation';
const template:Rubric={templateId:'t',templateVersion:1,criteria:[{criterionCode:'A',label:'A',weight:40},{criterionCode:'B',label:'B',weight:60}]};
const scores=(a:string,b:string):ScoreDraft[]=>[{criterionCode:'A',score:a,comment:'',evidenceFileIds:[]},{criterionCode:'B',score:b,comment:'',evidenceFileIds:[]}];
test('T3-12 weighted preview is 76.00 and rounds HALF_UP only once',()=>{
  expect(previewTotal(template,scores('5','3'))).toBe('76.00');
  const t={...template,criteria:[{criterionCode:'A',label:'A',weight:12.5},{criterionCode:'B',label:'B',weight:87.5}]};
  expect(previewTotal(t,scores('0.01','0'))).toBe('0.03');expect(previewTotal(t,scores('0.01','0.01'))).toBe('0.20');
});
test('T3-12 score rejects coercion, empty values and precision loss',()=>{
  for(const x of ['', ' ', '-1','5.01','0.001','1e0','NaN','0x1','1,5'])expect(scoreValid(x),x).toBe(false);
  for(const x of ['0','0.01','5','5.00','4.99'])expect(scoreValid(x),x).toBe(true);
});
test('T3-12 rubric and criterion completeness reject invalid drafts',()=>{
  expect(rubricValid(template)).toBe(true);expect(rubricValid({...template,criteria:[{criterionCode:'A',label:'A',weight:99}]})).toBe(false);
  expect(scoresValid(template,[scores('1','1')[0],scores('1','1')[0]])).toBe(false);
  expect(previewTotal(template,scores('','0'))).toBeNull();
});
test('T3-12 student accepts only canonical HIDDEN or authorized published data',()=>{
  expect(studentEvaluation({caseId:'c',visibility:'HIDDEN',evaluation:null,reasonCode:'NOT_AVAILABLE'},'c')).toBeNull();
  const v={caseId:'c',state:'DRAFT',version:1} as Evaluation;
  expect(()=>studentEvaluation({caseId:'c',visibility:'HIDDEN',evaluation:v,reasonCode:'NOT_AVAILABLE'},'c')).toThrow();
  expect(()=>studentEvaluation({caseId:'c',visibility:'VISIBLE',evaluation:v,reasonCode:'VISIBLE'},'c')).toThrow();
  expect(()=>studentEvaluation({caseId:'other',visibility:'HIDDEN',evaluation:null,reasonCode:'NOT_AVAILABLE'},'c')).toThrow();
});
