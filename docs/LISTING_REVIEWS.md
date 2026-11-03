# Public listing reviews

`GET /api/listings/{id}/reviews?target=ROOM&roomId={roomId}&page=0&size=4`
is a compatibility response adapter for the listing UI. It delegates all access,
visibility, pagination and rating calculations to the canonical `ReviewService`.
The `target` parameter selects this adapter. Without `target`, the same route
returns the standard review page from `ReviewController`, documented in `api.md`.

It returns visible reviews for the selected room. Omitting `roomId` includes rooms
in the listing's property. `target=LANDLORD` returns reviews for that property's
landlord across their properties, independent of the selected room.

The normal API envelope contains `summary` and `reviews`:

- Summary: average rating, total visible reviews, reply percentage and 1–5 star
  distribution across all matching reviews, independent of pagination.
- Reviews: the normal page response, newest first with ID as a stable tie breaker.
  Items include author display name, score, comment, landlord reply, date and a
  flag for a matching rental agreement that satisfies the canonical confirmed-stay
  eligibility rule. No contact,
  identity or contract identifiers are included.

Hidden reviews are excluded from both the list and summary. Hidden, expired or
missing listings return 404. Selecting a room outside the listing's property
returns 404. Page sizes follow the canonical API limit of 50.

An empty summary has null average/reply percentage and zero counts. The UI must
not replace these with demo ratings. Existing reviews have an overall score,
not separate cleanliness/location/amenity scores or chat response metrics.

Review creation, replies and moderation use the canonical endpoints documented
in `api.md`; the adapter does not duplicate their business logic.
