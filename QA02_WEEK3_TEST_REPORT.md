\# QA02 – WEEK 3 TEST REPORT



\*\*Tester:\*\* Nguyễn Gia Minh  

\*\*Role:\*\* QA02  

\*\*Scope:\*\* Invitation, Consent, Wrong Recipient và Placement



\## 1. Test execution results



\### Test 1: EnterpriseProposalIT



Command:

```powershell

.\\mvnw.cmd "-Dtest=EnterpriseProposalIT#browserUsesRealSchoolStudentSharingAndPlacementHandoff" "-Dt5.week3.browser=true" test

```



Result:

\- Tests run: 1

\- Failures: 0

\- Errors: 0

\- Skipped: 0

\- Status: \*\*BUILD SUCCESS\*\*



\### Test 2: Week2WorkflowIT



Command:

```powershell

.\\mvnw.cmd "-Dtest=Week2WorkflowIT" test

```



Result:

\- Tests run: 1

\- Failures: 0

\- Errors: 0

\- Skipped: 0

\- Status: \*\*BUILD SUCCESS\*\*



\## 2. Verified scenarios



\- Student accepts an invitation.

\- Accepting an invitation does not automatically grant profile-sharing consent.

\- HR cannot access student profile before consent is granted.

\- Student grants consent as a separate action.

\- School sharing approval is handled separately.

\- Placement is created and approved through a separate school workflow.

\- Revoking consent removes HR access to the shared profile.

\- Wrong recipient cannot respond to another student's invitation (HTTP 404).

\- Forged actorId is rejected (HTTP 400).

\- Invitation expiration and concurrent responses are checked.



\## 3. Overall result



\*\*PASS – No failures or errors observed in the executed test suites.\*\*



Note: Results confirm the scenarios covered by the executed tests, not every function of the entire system.



\*\*Tester:\*\* QA02 – Nguyễn Gia Minh  

\*\*Date:\*\* 17/11/2026

