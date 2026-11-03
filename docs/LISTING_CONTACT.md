# Listing phone reveal

`POST /api/listings/{id}/reveal-phone` is available to guests and signed-in users.
The response uses the normal API envelope with `data: { "phone": "..." }`.
The phone comes from the property's landlord account and is not added to public
listing responses. Hidden, expired and missing listings return 404.

If the landlord has no phone, the endpoint returns `data: { "phone": null }`.
A successful reveal increments `listings.contact_count` atomically, except when
the authenticated viewer owns the listing. Missing phones and failed requests
do not increment the counter. No database migration is needed.

The detail page requests the number only after clicking the reveal button. It
blocks duplicate clicks while loading and keeps the result when changing rooms
within the same listing. Once revealed, the number is a `tel:` link, so calling
does not send another reveal request. Navigating to another listing clears it.

This is a total contact counter, including historical contact data. It does not
yet represent unique visitors or a phone conversion rate. Future conversion
reporting should define the view denominator, time window, event type and
visitor deduplication before using the counter in a ratio.
