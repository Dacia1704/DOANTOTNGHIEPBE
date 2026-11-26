"""Deterministic fictional identities and housing descriptions for local seed data."""

import hashlib
import unicodedata

SURNAMES = [
    'Nguyễn',
    'Trần',
    'Lê',
    'Phạm',
    'Hoàng',
    'Huỳnh',
    'Phan',
    'Vũ',
    'Võ',
    'Đặng',
    'Bùi',
    'Đỗ',
    'Hồ',
    'Ngô',
    'Dương',
    'Lý',
    'Đinh',
    'Đoàn',
    'Trịnh',
    'Tạ',
    'Cao',
    'Lâm',
]
MALE_MIDDLES = ['Minh', 'Quang', 'Đức', 'Gia', 'Anh', 'Hoàng', 'Thanh', 'Duy', 'Tuấn', 'Nhật', 'Quốc', 'Hữu']
MALE_GIVEN = [
    'Huy',
    'Khang',
    'Phúc',
    'Bảo',
    'Long',
    'Khôi',
    'Nam',
    'Hải',
    'Dũng',
    'Kiên',
    'Lâm',
    'Sơn',
    'Vinh',
    'Thành',
    'Phong',
    'Quân',
    'Đạt',
    'Trung',
    'Tùng',
    'Hưng',
    'Luân',
    'Hiếu',
    'Khoa',
    'Tài',
]
FEMALE_MIDDLES = ['Ngọc', 'Thu', 'Phương', 'Khánh', 'Bảo', 'Thanh', 'Thùy', 'Mai', 'Như', 'Diệu', 'Tú', 'Kim']
FEMALE_GIVEN = [
    'Anh',
    'Linh',
    'Trang',
    'Hà',
    'Chi',
    'Thảo',
    'Nhi',
    'Vy',
    'Hương',
    'Ngân',
    'Quỳnh',
    'Yến',
    'Trâm',
    'Uyên',
    'Hạnh',
    'My',
    'Châu',
    'Oanh',
    'Loan',
    'Nhung',
    'Nga',
    'Lan',
    'Thư',
    'Dung',
]
HOUSE_NAMES = [
    'An Nhiên',
    'Minh Khang',
    'Thanh Bình',
    'Mộc An',
    'Nhà Xanh',
    'Góc Phố',
    'Bình Minh',
    'An Phú',
    'Hương Sen',
    'Mây Trắng',
    'Gia Hưng',
    'Thiên An',
    'Hải Đăng',
    'Tường Vy',
    'Vườn Nhỏ',
    'Ngọc Lan',
    'Sông Xanh',
    'Phúc An',
    'Ban Mai',
    'Bảo Ngọc',
    'Nắng Mai',
    'Hoàng Gia',
    'Trúc Xanh',
    'Tân Phú',
    'An Lạc',
    'Đại Phúc',
    'Bình An',
    'Mộc Lan',
    'Hoàng Yến',
    'Hạ Vy',
    'Gia Bảo',
    'Đông Phong',
    'Thảo Nguyên',
    'Hòa Bình',
    'Nam Phương',
]
STREETS = {
    'Hà Nội': [
        'Nguyễn Văn Huyên',
        'Hoàng Quốc Việt',
        'Trần Thái Tông',
        'Nguyễn Khang',
        'Dương Quảng Hàm',
        'Lê Trọng Tấn',
    ],
    'Hồ Chí Minh': [
        'Nguyễn Văn Đậu',
        'Phan Văn Trị',
        'Lê Quang Định',
        'Hoàng Hoa Thám',
        'Dương Quảng Hàm',
        'Nguyễn Thị Thập',
    ],
    'Đà Nẵng': ['Nguyễn Lương Bằng', 'Tôn Đức Thắng', 'Ngô Quyền', 'Hà Huy Tập', 'Điện Biên Phủ', 'Lê Duẩn'],
    'Cần Thơ': ['Nguyễn Văn Cừ', 'Mậu Thân', 'Trần Hoàng Na', 'Lý Tự Trọng', 'Nguyễn Tri Phương', 'Võ Văn Kiệt'],
}

def ascii_slug(text, separator='-'):
    text = unicodedata.normalize('NFD', text.lower().replace('đ', 'd'))
    text = ''.join(char for char in text if not unicodedata.combining(char))
    return separator.join(''.join(char if char.isalnum() else ' ' for char in text).split())

def account_identity(role, number):
    if role not in ('admin', 'landlord', 'tenant') or number < 1:
        raise ValueError('Unknown role or invalid account number')
    digest = hashlib.sha256(f'{role}/{number}/vietnamese-name'.encode()).digest()
    female = digest[0] % 2 == 0
    middle = (FEMALE_MIDDLES if female else MALE_MIDDLES)[digest[2] % 12]
    given = (FEMALE_GIVEN if female else MALE_GIVEN)[digest[3] % 24]
    full_name = f'{SURNAMES[digest[1] % len(SURNAMES)]} {middle} {given}'
    # Preserve the three convenient logins; other addresses use a reserved test domain.

    email = (
        f'{role}@gmail.com'
        if number == 1
        else f'{ascii_slug(full_name, ".")}.{role[0]}{number:04d}@trotot.example.test'
    )
    return {'full_name': full_name, 'email': email}

def property_identity(owner_number, property_number, label, province_name):
    index = owner_number * 17 + property_number * 11
    brand = HOUSE_NAMES[index % len(HOUSE_NAMES)]
    streets = next(
        (items for city, items in STREETS.items() if city in province_name),
        ['Lê Lợi', 'Trần Phú', 'Nguyễn Trãi', 'Nguyễn Du', 'Lý Thường Kiệt', 'Hoàng Diệu'],
    )
    street = streets[index % len(streets)]
    house = 18 + index % 180
    alley = 2 + (index * 7) % 48
    address = (
        f'Ngõ {house}, phố {street}'
        if 'Hà Nội' in province_name
        else f'{house}/{alley} đường {street}' if 'Hồ Chí Minh' in province_name else f'{house} đường {street}'
    )
    return {'name': f'{label} {brand}', 'address_line': address}
