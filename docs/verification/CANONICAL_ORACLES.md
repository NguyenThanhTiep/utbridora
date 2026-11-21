# Canonical requirement oracles

Frozen approved inputs remain byte-for-byte unchanged: SRS v1.2.4 and Contract Pack v1.1, with the hashes in `requirements.json`. The user approved both versions on13 November2026; historical Candidate/PENDING labels do not revoke that decision. The separate FR27 hiring-interest extension has its own approval record.

| Historical source | Application / canonical oracle |
| --- | --- |
| T4 k=3, exact enterprise totals/percentages, barrier breakdown, unrestricted reindex | **SUPERSEDED** by SRS24.2,25.8 and approved Contract v1.1: k=5, ledger and secondary suppression; closed release by default; no student identifiers for Enterprise. Existing raw documents are retained for provenance. |
| Original NFR-T4-01 with missing numeric values and50 concurrent requests | Quantitative demo benchmark uses SRS24.3 SYS-NFR-02/03:400×5 and5000×10, concurrency10,20warmups,100measured requests; R31 fixes4vCPU/8GB SSD. SYS-NFR-04 separately measures300 requests/endpoint with50sessions. No invented SLA fills the old missing values. |
| Repeated NFR-01..12 identifiers across T1 and T3 | Keys are qualified by team in RTM. T3's NFR cannot inherit a T1 PASS. |
| Repeated acceptance numbers from original documents | Require team/requirement/source locator together; an unqualified AC number is not a canonical identifier. |
| Legacy invitation/session enums and cut routes | Frozen v1.1 schemas and current compatibility mappings are the technical oracle; historical examples do not authorize new endpoints. |
| Earlier OPEN academic/permission/closure descriptions | SRS25.8 precedence and approved v1.1 owner ports govern implementation. Remaining production policy/signatures are still separate. |
| Hiring interest proposal's historical pending sentence | Separate `docs/contracts/extensions/T3_HIRING_INTEREST_APPROVAL.md` records user approval of the exact proposal hash. Scope remains exact-case HR intent, school read and terminal read-only. |

This index records applicability, not source-file edits, QA signatures or production approval. RTM exports preserve the Plan wording and mark missing per-requirement review explicitly.
