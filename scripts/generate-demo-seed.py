"""Regenerate V2 demo rows; preserve the checked-in administrative reference snapshot."""
from collections import defaultdict
from pathlib import Path
import json
import re
import uuid

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'src/main/resources/db/migration'
SEED = MIGRATIONS / 'V2__seed_reference_and_demo_data.sql'
PASSWORD = '$2a$10$RZccNo16W1rYiEPBjvswBu9IqqiiEtrEFNn9xltUs83/bQKuBzyBW'
AVATAR = 'https://img.icons8.com/nolan/1200/user-default.jpg'
IMAGES = [
    'https://images.pexels.com/photos/1643383/pexels-photo-1643383.jpeg?cs=srgb&dl=pexels-fotoaibe-1643383.jpg&fm=jpg',
    'https://tse4.mm.bing.net/th/id/OIP.r9I-If7EKBzj6RECd9adjQHaFj?r=0&pid=Api&h=220&P=0',
    'https://cdn.pixabay.com/photo/2023/12/30/07/31/living-room-8477521_1280.jpg',
]
old = SEED.read_text(encoding='utf-8')
if '-- BEGIN REFERENCE DATA' in old:
    reference = old.split('-- BEGIN REFERENCE DATA\n', 1)[1].split('-- END REFERENCE DATA', 1)[0]
else:
    # Keep the existing address snapshot and taxonomies verbatim, without the old demo users/assets.
    reference = old[old.index('-- 1. ROLES'):old.index('-- 2. FICTIONAL')]
    reference += old[old.index('-- 3. OFFICIAL'):old.index('-- 7. FICTIONAL')]

def uid(key):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'trotot-demo-v2/' + str(key)))

class SQL(str):
    pass

def at(days=0, minutes=0):
    return SQL(f'DATE_ADD(DATE_ADD(@seed_now, INTERVAL {days} DAY), INTERVAL {minutes} MINUTE)')

def date(days):
    return SQL(f'DATE({at(days)})')

def month_end(start_days, months):
    return SQL(f'DATE_ADD({at(start_days)}, INTERVAL {months} MONTH)')

def value(v):
    if isinstance(v, SQL): return v
    if v is None: return 'NULL'
    if isinstance(v, bool): return 'TRUE' if v else 'FALSE'
    if isinstance(v, (int, float)): return str(v)
    return "'" + str(v).replace("'", "''") + "'"

rows = defaultdict(list)
def add(table, **fields):
    rows[table].append(fields)
    return fields['id']

catalog_ids = {}
for table in ['property_types', 'room_types', 'amenities']:
    catalog_ids[table] = dict(re.findall(rf"INSERT INTO {table} .*?\nSELECT '([^']+)', '([^']+)'", reference))
    catalog_ids[table] = {name: id for id, name in catalog_ids[table].items()}

# Address IDs/codes come from the committed snapshot, not invented ward identifiers.
province_rows = re.findall(r"\('([^']+)', '([^']+)', '([^']+)'\)", reference)
ward_rows = re.findall(r"\('([^']+)', '([^']+)', '([^']+)', '([^']+)'\)", reference)
provinces = {code: (id, name) for id, code, name in province_rows}
wards = defaultdict(list)
for id, code, province_code, name in ward_rows:
    wards[provinces[province_code][0]].append((id, name))
if len(provinces) != 34 or sum(map(len, wards.values())) != 3321:
    raise ValueError('Unexpected address reference format/count')

users = {}
for role, count in [('admin', 3), ('landlord', 24), ('tenant', 100)]:
    for n in range(1, count + 1):
        suffix = '' if n == 1 else f'{n:02d}'
        key = f'{role}{n}'
        user_id = uid(key)
        users[key] = user_id
        add('users', id=user_id, email=f'{role}{suffix}@gmail.com', password=PASSWORD,
            full_name=f'{dict(admin="Quản trị", landlord="Chủ trọ", tenant="Người thuê")[role]} Demo {n:02d}',
            avatar_url=AVATAR, email_verified_at=at(-60), status='ACTIVE', created_at=at(-90))
        add('user_roles', id=uid('role/' + key), user_id=user_id,
            role_id=SQL(f"(SELECT id FROM roles WHERE code='{role.upper()}')"))

packages = [
    ('FREE', 'Miễn phí', 3, 0, 0, 0, False, 0),
    ('STARTER', 'Khởi đầu', 8, 1, 2, 0, False, 99000),
    ('STANDARD', 'Tiêu chuẩn', 15, 2, 4, 0, True, 199000),
    ('PRO', 'Chuyên nghiệp', 30, 3, 8, 1, True, 399000),
    ('BUSINESS', 'Kinh doanh', 60, 6, 15, 1, True, 699000),
    ('ENTERPRISE', 'Doanh nghiệp', None, 12, 30, 1, True, 1299000),
    ('LEGACY', 'Gói cũ ngừng bán', 10, 1, 2, 0, False, 149000),
]
package = {p[0]: p for p in packages}
for code, name, quota, high, low, priority, stats, monthly in packages:
    add('service_packages', id=uid('package/' + code), code=code, name=name, max_listings=quota,
        high_priority_slots=high, low_priority_slots=low, default_priority_level=priority,
        has_stats=stats, is_active=code != 'LEGACY')
    if code != 'FREE':
        for months in [1, 3, 6, 12]:
            add('service_package_prices', id=uid(f'price/{code}/{months}'), package_id=uid('package/' + code),
                duration_months=months, price=int(monthly * months * {1: 1, 3: .95, 6: .9, 12: .8}[months]),
                is_active=code != 'LEGACY')

boosts = []
for months in [1, 3, 6]:
    for level, high, low in [('MINI', 1, 2), ('PLUS', 2, 4), ('MAX', 4, 8)]:
        code = f'BOOST_{level}_{months}M'
        p = dict(id=uid(code), code=code, name=f'Boost {level.title()} {months} tháng',
                 price=(high * 59000 + low * 19000) * months, duration_months=months,
                 high_priority_slots=high, low_priority_slots=low, is_active=True)
        add('boost_service_packages', **p)
        boosts.append(p)
add('boost_service_packages', id=uid('boost-legacy'), code='BOOST_LEGACY', name='Boost cũ ngừng bán',
    price=49000, duration_months=1, high_priority_slots=1, low_priority_slots=0, is_active=False)

def rights(code):
    p = package[code]
    return dict(package_code_snapshot=code, package_name_snapshot=p[1], max_listings_snapshot=p[2],
                high_priority_slots_snapshot=p[3], low_priority_slots_snapshot=p[4],
                default_priority_level_snapshot=p[5], has_stats_snapshot=p[6])

def payment(owner, target, kind, amount, status, key, package_id, paid_days=-5, review=False):
    pid = uid('payment/' + key)
    add('payments', id=pid, landlord_id=owner, type=kind, amount=amount, payment_method='BANK_TRANSFER',
        status=status, service_subscription_order_id=target if kind == 'SERVICE_PACKAGE' else None,
        boost_subscription_id=target if kind == 'BOOST_PACKAGE' else None,
        merchant_order_id='DEMO-' + key, provider_txn_id='DEMO-TXN-' + key if status in ['SUCCESS', 'REFUNDED'] else None,
        paid_at=at(paid_days) if status in ['SUCCESS', 'REFUNDED'] else None,
        expires_at=at(1) if status == 'PENDING' else at(paid_days, 15),
        created_at=at(paid_days), requires_review=review,
        review_reason='Giao dịch demo cần đối soát thủ công' if review else None)
    if status == 'SUCCESS':
        add('invoices', id=uid('invoice/' + key), payment_id=pid, type=kind,
            service_package_id=package_id if kind == 'SERVICE_PACKAGE' else None,
            boost_service_package_id=package_id if kind == 'BOOST_PACKAGE' else None,
            invoice_no='DEMO-INV-' + key, total_amount=amount, tax_amount=0,
            buyer_name='Khách hàng Demo', issued_at=at(paid_days), created_at=at(paid_days))

active_periods = {}
for n in range(1, 25):
    owner = users[f'landlord{n}']
    for state, code, start, end in ([('EXPIRED', 'STARTER', -180, -90)] if n % 4 in [1, 2] else []) + (
        [('ACTIVE', 'PRO' if n % 4 == 1 else 'STANDARD', -5, 85)] if n % 4 in [1, 2] else []):
        key = f'service-{n}-{state}'
        sid, oid, period = uid('sub/' + key), uid('order/' + key), uid('period/' + key)
        amount = int(package[code][7] * 3 * .95)
        end = month_end(start, 3)
        add('service_package_subscriptions', id=sid, landlord_id=owner, package_id=uid('package/' + code),
            start_at=at(start), end_at=end, status=state, created_at=at(start))
        add('service_subscription_orders', id=oid, landlord_id=owner, package_id=uid('package/' + code),
            service_package_price_id=uid(f'price/{code}/3'), subscription_id=sid, type='PURCHASE', status='APPLIED',
            duration_months=3, price=amount, **rights(code), expires_at=at(start, 15), effective_at=at(start),
            end_at=end, applied_at=at(start), created_at=at(start))
        add('service_subscription_periods', id=period, subscription_id=sid, order_id=oid, period_number=1,
            start_at=at(start), end_at=end, **rights(code), entitlement_value=amount, billing_months=3)
        payment(owner, oid, 'SERVICE_PACKAGE', amount, 'SUCCESS', key, uid('package/' + code), start)
        if state == 'ACTIVE': active_periods[n] = (period, code)
    # Quote/payment states for history, checkout and admin review screens.
    code = 'BUSINESS'
    status = ['PENDING_PAYMENT', 'CANCELLED', 'EXPIRED', 'REQUIRES_REVIEW'][n % 4]
    pstatus = {'PENDING_PAYMENT': 'PENDING', 'CANCELLED': 'FAILED', 'EXPIRED': 'EXPIRED', 'REQUIRES_REVIEW': 'SUCCESS'}[status]
    key, oid = f'quote-{n}', uid(f'quote-{n}')
    add('service_subscription_orders', id=oid, landlord_id=owner, package_id=uid('package/' + code),
        service_package_price_id=uid(f'price/{code}/1'), type='CHANGE' if n in active_periods else 'PURCHASE',
        previous_subscription_id=uid(f'sub/service-{n}-ACTIVE') if n in active_periods else None,
        status=status, duration_months=1,
        price=package[code][7], **rights(code), expires_at=at(1) if status == 'PENDING_PAYMENT' else at(-1), created_at=at(-2))
    payment(owner, oid, 'SERVICE_PACKAGE', package[code][7], pstatus, key, uid('package/' + code), -2, status == 'REQUIRES_REVIEW')

active_boosts = {}
for n in range(1, 25):
    owner = users[f'landlord{n}']
    for state in ['EXPIRED', 'CANCELLED', 'PENDING_PAYMENT'] + (['ACTIVE'] if n % 4 in [1, 3] else []):
        p = boosts[(n - 1) % len(boosts)]
        key, sid = f'boost-{n}-{state}', uid(f'boost-{n}-{state}')
        start = -210 if state == 'EXPIRED' else -5
        end = month_end(start, p['duration_months'])
        dated = state in ['ACTIVE', 'EXPIRED']
        add('boost_service_package_subscriptions', id=sid, landlord_id=owner, boost_package_id=p['id'],
            status=state, price=p['price'], package_name_snapshot=p['name'],
            duration_months_snapshot=p['duration_months'], high_priority_slots_snapshot=p['high_priority_slots'],
            low_priority_slots_snapshot=p['low_priority_slots'], payment_expires_at=at(1) if state == 'PENDING_PAYMENT' else at(start, 15),
            start_at=at(start) if dated else None, end_at=end if dated else None, created_at=at(start))
        ps = 'SUCCESS' if dated else 'PENDING' if state == 'PENDING_PAYMENT' else 'REFUNDED'
        payment(owner, sid, 'BOOST_PACKAGE', p['price'], ps, key, p['id'], start)
        if state == 'ACTIVE': active_boosts[n] = sid

# Coordinates are fictional nearby points for map/filter demos, not verified street addresses.
centers = {'01': (21.0285, 105.8542), '79': (10.7769, 106.7009), '48': (16.0544, 108.2022),
           '31': (20.8449, 106.6881), '92': (10.0452, 105.7469), '46': (16.4637, 107.5909)}
cities = list(centers)
published = []
first_rooms = {}
for n in range(1, 25):
    paid = n in active_periods
    for k in range(8):
        key = f'{n}/{k}'
        city = cities[(n + k - 1) % len(cities)]
        province_id, province_name = provinces[city]
        ward_id, ward_name = wards[province_id][(n * 7 + k) % len(wards[province_id])]
        whole = k == 7
        property_id = uid('property/' + key)
        label = ('Nhà nguyên căn' if whole else ['Nhà trọ', 'Căn hộ dịch vụ', 'Khu lưu trú'][k % 3])
        pname = f'{label} Demo {n:02d}-{k + 1:02d}'
        lat, lng = centers[city]
        add('properties', id=property_id, landlord_id=users[f'landlord{n}'],
            property_type_id=catalog_ids['property_types']['Nhà riêng' if whole else ['Nhà trọ', 'Tòa căn hộ dịch vụ', 'Khu lưu trú sinh viên'][k % 3]],
            name=pname, rental_mode='WHOLE' if whole else 'BY_ROOM', total_floors=2 if whole else 4,
            province_id=province_id, ward_id=ward_id, address_line=f'{20 + n * 3 + k} đường Demo, {ward_name}',
            latitude=round(lat + ((n % 7) - 3) * .006 + k * .002, 8),
            longitude=round(lng + ((n % 5) - 2) * .008 + k * .002, 8))
        for order, image in enumerate(IMAGES):
            add('property_images', id=uid(f'property-image/{key}/{order}'), property_id=property_id,
                url=image, public_id=None, sort_order=order)
        for name in ['WiFi', 'Chỗ để xe', 'Camera an ninh', 'Máy giặt dùng chung']:
            add('property_amenities', id=uid(f'property-amenity/{key}/{name}'), property_id=property_id,
                amenity_id=catalog_ids['amenities'][name])
        for r in range(1 if whole else 8):
            room_id = uid(f'room/{key}/{r}')
            first_rooms[property_id] = first_rooms.get(property_id, room_id)
            price = (6500000 + n * 150000) if whole else (1400000 + ((n + k + r) % 20) * 250000)
            room_type = 'Nhà riêng nhiều tầng' if whole else ['Phòng khép kín tiêu chuẩn', 'Phòng khép kín có gác', 'Căn hộ studio', 'Căn hộ một phòng ngủ'][r % 4]
            add('rooms', id=room_id, property_id=property_id, room_type_id=catalog_ids['room_types'][room_type],
                room_number='WHOLE' if whole else f'{r // 2 + 1}0{r % 2 + 1}', floor_number=None if whole else r // 2 + 1,
                price=price, electricity_price=None if whole else 3500, water_price=None if whole else 20000,
                service_fee=None if whole else 100000 + (r % 3) * 50000, default_deposit=price,
                area_m2=85 + n if whole else 18 + ((n + k + r) % 25) * 2, max_occupants=6 if whole else 2 + r % 3,
                rental_status='MAINTENANCE' if r == 7 and n % 3 == 0 else 'AVAILABLE')
            for order, image in enumerate(IMAGES):
                add('room_images', id=uid(f'room-image/{key}/{r}/{order}'), room_id=room_id,
                    url=image, public_id=None, sort_order=order)
            for name in ['Nhà vệ sinh riêng', 'Cửa sổ', 'Giường'] + (['Điều hòa', 'Bếp riêng'] if r % 2 == 0 else ['Ban công', 'Tủ quần áo']):
                add('room_amenities', id=uid(f'room-amenity/{key}/{r}/{name}'), room_id=room_id,
                    amenity_id=catalog_ids['amenities'][name])
        state = ['PUBLISHED', 'PUBLISHED', 'PUBLISHED' if paid else 'HIDDEN', 'PENDING', 'REJECTED', 'HIDDEN', 'EXPIRED', 'PUBLISHED' if paid else 'HIDDEN'][k]
        listing_id = uid('listing/' + key)
        period, code = active_periods.get(n, (None, 'FREE'))
        package_priority = 2 if paid and state == 'PUBLISHED' and k == 0 else 0
        boost_priority = (2 if k == 0 else 1) if n in active_boosts and k in [0, 1] else 0
        add('listings', id=listing_id, property_id=property_id,
            title=f'{pname} - {province_name} - thoáng sáng, tiện đi lại', slug=f'cho-thue-demo-{n:02d}-{k + 1:02d}',
            description=f'Cơ sở minh họa tại {ward_name}, {province_name}. Phòng đa dạng diện tích, có ảnh, giá thuê và tiện ích rõ ràng. Dữ liệu demo, địa chỉ và tọa độ mô phỏng.',
            status=state, rejection_reason='Cần bổ sung nội dung mô tả và xác minh thông tin (demo).' if state == 'REJECTED' else None,
            approved_by=users['admin1'] if state in ['PUBLISHED', 'HIDDEN', 'EXPIRED'] else None,
            approved_at=at(-10) if state in ['PUBLISHED', 'HIDDEN', 'EXPIRED'] else None,
            submitted_at=at(-1), pending_expires_at=at(2) if state == 'PENDING' else None,
            published_at=at(-60) if state == 'EXPIRED' else at(-4) if state in ['PUBLISHED', 'HIDDEN'] else None,
            expires_at=at(-30) if state == 'EXPIRED' else at(25) if state in ['PUBLISHED', 'HIDDEN'] else None,
            service_subscription_period_id=period if package_priority else None, package_priority_level=package_priority,
            priority_level=max(package_priority, boost_priority), view_count=0, contact_count=0, created_at=at(-65))
        if n in active_boosts and k in [0, 1]:
            add('boost_allocations', id=uid('allocation/' + key), boost_subscription_id=active_boosts[n],
                listing_id=listing_id, priority_level=boost_priority)
        if state == 'PUBLISHED': published.append((listing_id, property_id, n, province_id, ward_id))

for n in range(1, 101):
    tenant = users[f'tenant{n}']
    listing_id, property_id, owner_number, province_id, ward_id = published[(n - 1) % len(published)]
    profile_id = uid(f'tenant-profile/{n}')
    add('tenant_profiles', id=profile_id, user_id=tenant, desired_province_id=province_id, desired_ward_id=ward_id,
        price_min=1500000, price_max=3000000 + n % 8 * 500000, area_min=18, area_max=60,
        desired_room_type_id=catalog_ids['room_types']['Phòng khép kín tiêu chuẩn'],
        tenant_type=['STUDENT', 'WORKER', 'FAMILY', 'OTHER'][n % 4], occupants=1 + n % 3,
        move_in_date=date(14), max_distance_km=5 + n % 6, price_tolerance_pct=20,
        top_priority=['PRICE', 'LOCATION', 'AREA', 'AMENITIES'][n % 4], allow_personalization=n % 10 != 0)
    add('tenant_profile_wards', id=uid(f'tenant-ward/{n}'), tenant_profile_id=profile_id, ward_id=ward_id)
    for name in ['WiFi', 'Nhà vệ sinh riêng']:
        add('tenant_profile_amenities', id=uid(f'tenant-amenity/{n}/{name}'), tenant_profile_id=profile_id,
            amenity_id=catalog_ids['amenities'][name], is_required=name == 'Nhà vệ sinh riêng')
    for offset in range(5):
        lid = published[(n + offset) % len(published)][0]
        add('saved_listings', id=uid(f'saved/{n}/{offset}'), tenant_id=tenant, listing_id=lid)
        add('listing_view_history', id=uid(f'view/{n}/{offset}'), tenant_id=tenant, listing_id=lid, created_at=at(-offset))
        if offset < 2: add('listing_contact_events', id=uid(f'contact/{n}/{offset}'), tenant_id=tenant, listing_id=lid, created_at=at(-offset))
    add('listing_search_history', id=uid(f'search/{n}'), user_id=tenant, source=['SEARCH_PAGE', 'MAP', 'CHATBOT'][n % 3],
        keyword='phòng trọ', province_id=province_id, ward_id=ward_id, price_min=1500000, price_max=6500000,
        area_min=18, area_max=60, result_count=20, created_at=at(-1))
    conversation = uid(f'chat/{n}')
    add('chat_conversations', id=conversation, listing_id=listing_id, last_message_at=at(0, -10), created_at=at(-2))
    for user in [tenant, users[f'landlord{owner_number}']]:
        add('chat_conversation_participants', id=uid(f'chat-participant/{n}/{user}'), conversation_id=conversation, user_id=user, joined_at=at(-2))
    for m, text in enumerate(['Chào chủ trọ, phòng này còn trống không ạ?', 'Chào bạn, cơ sở vẫn còn phòng. Bạn muốn xem phòng vào thời gian nào?', 'Chiều cuối tuần em qua xem được không ạ?', 'Được bạn nhé, bạn nhắn trước khi đến giúp mình.']):
        add('chat_messages', id=uid(f'chat-message/{n}/{m}'), conversation_id=conversation,
            sender_id=tenant if m % 2 == 0 else users[f'landlord{owner_number}'], content=text, created_at=at(-1, m * 10))
    if n <= 60:
        state = ['PENDING', 'REJECTED', 'CANCELLED', 'EXPIRED'][n % 4]
        add('rental_requests', id=uid(f'request/{n}'), listing_id=listing_id, room_id=first_rooms[property_id],
            tenant_id=tenant, landlord_id=users[f'landlord{owner_number}'], message='Em muốn thuê và hẹn xem phòng (dữ liệu demo).',
            proposed_move_in_date=date(14), tenant_full_name=f'Người thuê Demo {n:02d}',
            tenant_email=('tenant' if n == 1 else f'tenant{n:02d}') + '@gmail.com',
            occupant_count=1 + n % 3, duration_months=6, status=state,
            expires_at=at(2) if state == 'PENDING' else at(-1),
            responded_at=None if state in ['PENDING', 'EXPIRED'] else at(-1), created_at=at(-2))
    add('notifications', id=uid(f'notification/{n}'), user_id=tenant, type='SYSTEM', title='Chào mừng đến Trọ Tốt',
        body='Tài khoản demo đã sẵn sàng. Bạn có thể tìm trọ, lưu tin và nhắn tin với chủ trọ.', is_read=n % 3 == 0)

# Event-backed counters stay consistent with their histories and daily breakdown.
for lid, _, _, _, _ in published:
    for d in range(5):
        views = sum(r['listing_id'] == lid and r['created_at'] == at(-d) for r in rows['listing_view_history'])
        contacts = sum(r['listing_id'] == lid and r['created_at'] == at(-d) for r in rows['listing_contact_events'])
        add('listing_daily_stats', id=uid(f'stat/{lid}/{d}'), listing_id=lid, stat_date=date(-d), view_count=views, contact_count=contacts)

out = ["-- V2: Reference catalog and fictional demo data for a fresh database.\n"
       "-- All 127 accounts use BCrypt(10) password 123456 and @gmail.com emails.\n"
       "-- Example accounts: admin@gmail.com, landlord@gmail.com, tenant@gmail.com.\n"
       "-- Relative timestamps keep active subscriptions/listings usable on migration day.\n"
       "-- External image URLs have NULL Cloudinary public_id. No remote payments/mail/uploads are performed.\n"
       "-- Generated by scripts/generate-demo-seed.py; see docs/DEMO_DATABASE.md.\n\n"
       "SET NAMES utf8mb4;\nSET @seed_previous_time_zone = @@session.time_zone;\nSET time_zone = '+07:00';\nSET @seed_now = CURRENT_TIMESTAMP;\n\n-- BEGIN REFERENCE DATA\n",
       reference, '-- END REFERENCE DATA\n\n']
# Cross-domain billing foreign keys require this explicit topological ordering.
order = ['users', 'user_roles', 'service_packages', 'service_package_prices', 'boost_service_packages',
         'service_package_subscriptions', 'service_subscription_orders', 'service_subscription_periods',
         'boost_service_package_subscriptions', 'payments', 'invoices', 'properties', 'property_images',
         'property_amenities', 'rooms', 'room_images', 'room_amenities', 'listings', 'boost_allocations',
         'tenant_profiles', 'tenant_profile_wards', 'tenant_profile_amenities', 'saved_listings',
         'listing_view_history', 'listing_contact_events', 'listing_search_history', 'chat_conversations',
         'chat_conversation_participants', 'chat_messages', 'rental_requests', 'notifications', 'listing_daily_stats']
assert set(order) == set(rows)
for table in order:
    entries = rows[table]
    columns = list(dict.fromkeys(k for entry in entries for k in entry))
    out.append(f'-- {table}: {len(entries)} rows\n')
    for offset in range(0, len(entries), 200):
        out.append(f'INSERT INTO `{table}` (' + ', '.join(f'`{c}`' for c in columns) + ') VALUES\n')
        out.append(',\n'.join('(' + ', '.join(value(entry.get(c)) for c in columns) + ')' for entry in entries[offset:offset+200]) + ';\n\n')
out.append('UPDATE listings l SET view_count=(SELECT COUNT(*) FROM listing_view_history v WHERE v.listing_id=l.id),\n'
           'contact_count=(SELECT COUNT(*) FROM listing_contact_events c WHERE c.listing_id=l.id);\n'
           'SET time_zone = @seed_previous_time_zone;\n')
SEED.write_text(''.join(out), encoding='utf-8')
print(json.dumps({table: len(entries) for table, entries in rows.items()}, indent=2))
