"""Generate the V12 community demo migration for a fresh project database."""

import argparse
import json
from collections import Counter
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / 'src/main/resources/db/migration/V12__seed_community_demo_data.sql'
IMAGES = [
    'https://dynamic-media-cdn.tripadvisor.com/media/photo-o/28/ed/f5/e3/jewel-punta-cana-all.jpg?w=1400&h=-1&s=1',
    'https://images.stockcake.com/public/c/0/1/c01d3664-8c4c-46d8-85c6-ec2a3b9be90e_large/luxurious-beach-resort-stockcake.jpg',
    'https://tse4.mm.bing.net/th/id/OIP.21ZvL7uodC5HIbuO-eiXWwHaEo?r=0&pid=Api&h=220&P=0',
]
AREAS = ['Bình Thạnh', 'Thủ Đức', 'Gò Vấp', 'Tân Bình', 'Quận 7', 'Bình Tân',
         'Cầu Giấy', 'Đống Đa', 'Thanh Xuân', 'Hà Đông', 'Liên Chiểu', 'Hải Châu',
         'Sơn Trà', 'Ninh Kiều', 'Dĩ An', 'Biên Hòa']
TOPICS = [
    ('Tìm bạn ở ghép', 'Mình đang tìm bạn ở ghép tại {area}, ngân sách khoảng {budget} triệu/người/tháng. Ưu tiên người gọn gàng, tôn trọng giờ nghỉ và trao đổi rõ chi phí sinh hoạt.', '#oghep #timban'),
    ('Kinh nghiệm xem phòng', 'Cuối tuần này mình đi xem phòng tại {area}. Mình ghi lại checklist: thử áp lực nước, kiểm tra ổ điện, hỏi chỗ phơi đồ, giờ giấc và xem hợp đồng trước khi đặt cọc.', '#kinhnghiem #xemphong'),
    ('Góc học tập ở trọ', 'Mình vừa sắp xếp lại góc học tập ở phòng thuê tại {area}. Chỉ cần một chiếc đèn đủ sáng, bàn nhỏ sát cửa sổ và kệ để sách là phòng gọn hơn nhiều.', '#gochoctap #decor'),
    ('Hỏi kinh nghiệm chuyển trọ', 'Sắp chuyển sang {area}, mình muốn xin kinh nghiệm thuê xe chở đồ và đóng gói máy tính. Mọi người thường chuẩn bị trước bao nhiêu ngày?', '#chuyentro #hoidap'),
    ('Chi phí sinh hoạt', 'Mình dự trù tổng ngân sách {budget} triệu mỗi tháng khi thuê tại {area}. Ngoài tiền phòng còn điện, nước, gửi xe, internet và đồ dùng chung. Có khoản nào dễ bị quên không?', '#chiphisinhhoat #thuenha'),
    ('Nấu ăn tiết kiệm', 'Một tuần tự nấu ăn ở trọ tại {area} giúp mình chủ động chi tiêu hơn. Mình chia nguyên liệu thành từng bữa, ghi ngày bảo quản và dùng hộp kín để tủ lạnh sạch sẽ.', '#nauan #songtietkiem'),
    ('Đọc hợp đồng trước khi ký', 'Mọi người tìm phòng tại {area} nhớ đọc kỹ điều khoản đặt cọc, báo trước khi chuyển đi, giá dịch vụ và cách chốt chỉ số điện nước. Có gì chưa rõ nên hỏi lại và lưu thỏa thuận bằng văn bản.', '#hopdong #datcoc'),
    ('Tìm quán học bài', 'Có ai biết thư viện hoặc quán yên tĩnh gần {area} không? Mình cần chỗ có ổ cắm, ánh sáng tốt, phù hợp học buổi tối và không quá đông.', '#hoidap #sinhvien'),
    ('Cách giữ phòng thoáng', 'Mùa mưa ở {area}, mình tranh thủ mở cửa khi trời ráo, tránh phơi đồ ướt trong phòng kín và vệ sinh khu bếp thường xuyên. Mọi người có mẹo chống ẩm nào dễ làm không?', '#meovat #phongtro'),
    ('Giao thông quanh khu trọ', 'Mình đang cân nhắc thuê tại {area}, tầm giá {budget} triệu. Muốn hỏi trải nghiệm đi xe buýt, chỗ gửi xe và thời gian di chuyển vào giờ cao điểm.', '#giaothong #timphong'),
    ('Chia sẻ tin phòng', 'Mình thấy một số tin phòng công khai tại {area} khá phù hợp ngân sách {budget} triệu. Mình gắn tin bên dưới để mọi người tham khảo, nhớ xem thực tế và xác nhận lại với chủ trọ.', '#chiasephong #timtro'),
    ('An toàn điện ở phòng thuê', 'Mọi người ở {area} lưu ý không cắm quá nhiều thiết bị công suất lớn vào một ổ chia. Nếu thấy dây điện nóng hoặc ổ cắm lỏng, nên báo người quản lý kiểm tra.', '#antoan #kinhnghiem'),
    ('Cuối tuần dọn phòng', 'Lịch cuối tuần của mình tại {area}: giặt ga gối, lau góc bếp, phân loại rác và kiểm tra đồ dùng sắp hết. Dọn mỗi lần một ít sẽ nhẹ nhàng hơn.', '#donphong #songgon'),
    ('Tìm phòng cho người đi làm', 'Mình làm việc theo ca và muốn tìm phòng yên tĩnh tại {area}, ngân sách {budget} triệu. Ưu tiên lối đi riêng và có thể trao đổi rõ lịch sinh hoạt với người ở cùng.', '#nguoidiLam #timphong'),
    ('Ứng xử với hàng xóm', 'Một lời chào và thông báo trước khi có bạn đến chơi giúp mình sống thoải mái hơn ở khu trọ {area}. Nếu có tiếng ồn, mình chọn trao đổi lịch sự, cụ thể về khung giờ.', '#vanminh #hangxom'),
    ('Chăm cây ở phòng trọ', 'Mình thử trồng một chậu cây nhỏ cạnh cửa sổ tại {area}. Phòng sáng vừa phải nên mình chọn loại dễ chăm, đặt khay hứng nước để không làm ẩm sàn.', '#gocxanh #decor'),
    ('Chuẩn bị nhập học', 'Tân sinh viên đang tìm phòng tại {area} có thể lập danh sách nhu cầu trước: khoảng cách đến trường, mức chi phí {budget} triệu, chỗ học và điều kiện đi lại khi trời mưa.', '#tansinhvien #timtro'),
    ('Nuôi thú cưng khi thuê', 'Có bạn nào đang thuê tại {area} và nuôi mèo không? Mình muốn hỏi cách trao đổi với chủ trọ, xử lý vệ sinh và bảo đảm không ảnh hưởng người ở xung quanh.', '#thucung #hoidap'),
    ('Phòng ồn, nên xử lý thế nào?', 'Mình đã thuê tại {area}, phòng buổi tối hơi ồn nên chưa hài lòng. Mình muốn tìm cách trao đổi lịch sự với hàng xóm và người quản lý trước khi quyết định chuyển đi.', '#trainghiem #tiengon'),
    ('Cảnh báo lừa đảo tiền cọc', 'Khi tìm phòng tại {area}, mình chỉ xem đây là lời nhắc chung: kiểm tra danh tính bên cho thuê, xem phòng thực tế, đọc hợp đồng và giữ chứng từ. Không vội chuyển khoản chỉ vì bị thúc giục.', '#antoanthue #canhbao'),
    ('Internet khi ở ghép', 'Nhóm mình ở {area} muốn chia sẻ mạng ổn định để học và làm việc. Có bạn nào có kinh nghiệm chọn vị trí router, trao đổi chi phí và khung giờ tải dữ liệu không?', '#internet #oghep'),
    ('Giữ giấy tờ thuê nhà', 'Mình tạo một thư mục riêng để giữ hợp đồng, biên nhận và ảnh chốt chỉ số tại phòng thuê ở {area}. Khi cần đối chiếu thì tìm lại rất nhanh.', '#hopdong #quanlychitieu'),
    ('Tìm bạn chạy bộ', 'Có ai ở gần {area} muốn lập nhóm đi bộ hoặc chạy nhẹ cuối tuần không? Mình muốn giữ lịch sinh hoạt đều đặn, tập ở nơi công cộng và ưu tiên an toàn.', '#ketnoi #songkhoe'),
    ('Góc thư giãn mình yêu thích', 'Mình thích không gian nhiều cây xanh và ánh sáng tự nhiên. Đang muốn sắp xếp lại góc nghỉ ngơi trong phòng tại {area}, mọi người có gợi ý nào dễ làm và tiết kiệm không?', '#camhung #khonggiansong'),
]
ROOT_COMMENTS = [
    'Cảm ơn bạn đã chia sẻ, checklist này rất hữu ích cho người mới tìm phòng.',
    'Mình cũng đang quan tâm khu {area}. Bạn có thể chia sẻ thêm về giờ giấc sinh hoạt không?',
    'Theo mình nên xem phòng vào cả ban ngày và buổi tối để cảm nhận ánh sáng và tiếng ồn.',
    'Nhớ hỏi rõ điện, nước, internet và phí gửi xe trước khi chốt tổng ngân sách nhé.',
    'Mình đồng ý việc giữ lại thỏa thuận bằng văn bản, như vậy hai bên đều dễ đối chiếu.',
    'Góc phòng gọn gàng quá. Mình sẽ thử áp dụng cách sắp xếp này vào cuối tuần.',
    'Nên kiểm tra đường đi khi trời mưa và giờ cao điểm nữa, có thể khác khá nhiều.',
    'Mình đang tìm bạn ở ghép có lịch sinh hoạt tương tự, rất vui nếu mọi người cùng trao đổi.',
    'Một nội dung đáng lưu lại. Cảm ơn bạn đã viết rõ từng ý.',
    'Ngân sách nên có thêm một khoản dự phòng cho đồ dùng và việc chuyển phòng.',
    'Mình thích cách bạn trao đổi thẳng thắn nhưng vẫn tôn trọng người khác.',
    'Thông tin ở {area} rất đúng nhu cầu của mình, mong được đọc thêm trải nghiệm thực tế.',
    'Góc này nhiều cây xanh, nhìn thư giãn thật. Mình cũng muốn sắp xếp lại chỗ nghỉ ngơi.',
    'Mình từng gặp trường hợp tương tự, nên chụp lại tình trạng phòng khi nhận bàn giao.',
    'Hãy hỏi kỹ quy định về khách đến chơi, thú cưng và chỗ nấu ăn trước khi đặt cọc.',
    'Phòng có thể nhỏ nhưng thông thoáng và dễ giữ sạch thì sinh hoạt sẽ thoải mái hơn nhiều.',
]
REPLIES = [
    'Cảm ơn góp ý của bạn! Mình sẽ bổ sung mục này vào danh sách chuẩn bị.',
    'Đúng rồi, hai bên nên thống nhất rõ trước để tránh hiểu nhầm về sau.',
    'Mình cũng có trải nghiệm tương tự, trao đổi lịch sự thường giải quyết được khá nhiều việc.',
    'Bạn thử ghi lại những khoản cần hỏi rồi đối chiếu khi đi xem thực tế nhé.',
    'Mình đã lưu bài để tham khảo, chúc bạn sớm tìm được chỗ ở phù hợp.',
    'Mỗi khu có điều kiện khác nhau, nên kiểm tra lại thông tin tại thời điểm đi xem phòng.',
    'Nếu có thay đổi mình sẽ cập nhật thêm trong bài, cảm ơn mọi người đã quan tâm.',
    'Nhóm mình thường chia sẻ công việc và chi phí theo lịch để ai cũng chủ động được.',
]


def uid(key):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'trotot-community-seed-v1/' + str(key)))


def quote(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    # Keep SQL independent of the connection's backslash-escape mode.
    return "'" + str(value).replace("'", "''") + "'"


def sql_uuid(expression):
    digest = f'SHA2({expression},256)'
    return f"CONCAT(SUBSTR({digest},1,8),'-',SUBSTR({digest},9,4),'-',SUBSTR({digest},13,4),'-',SUBSTR({digest},17,4),'-',SUBSTR({digest},21,12))"


def values(table, columns, rows):
    output = []
    for offset in range(0, len(rows), 150):
        chunk = rows[offset:offset + 150]
        output.append(f"INSERT INTO {table} ({','.join(columns)}) VALUES\n" + ',\n'.join('(' + ','.join(quote(v) for v in row) + ')' for row in chunk) + ';\n')
    return ''.join(output)


def build(post_count, migration=False, accounts=None):
    if accounts is None:
        accounts = json.loads((ROOT / 'scripts/data/seed-accounts.json').read_text(encoding='utf-8'))
    posts, comments, media = [], [], []
    for number in range(1, post_count + 1):
        area = AREAS[(number - 1) % len(AREAS)]
        title, body, tags = TOPICS[(number - 1) % len(TOPICS)]
        role = 'ADMIN' if number % 20 == 0 else 'LANDLORD' if number % 3 == 0 else 'TENANT'
        status = {0: 'PENDING', 1: 'HIDDEN', 2: 'RESTRICTED', 3: 'REMOVED'}.get(number % 30, 'VISIBLE')
        source = 'USER_REPORT' if number % 60 == 1 else 'MANUAL' if status == 'REMOVED' else 'AUTO_RULE'
        deleted = int(number % 30 == 4)
        version = 2 if number % 11 == 0 and status == 'VISIBLE' else 1
        text = f'{title} ở {area}\n\n' + body.format(area=area, budget=f'{2 + number % 5},{number % 10}')
        if role == 'ADMIN':
            text = f'Gửi các thành viên đang tìm trọ tại {area}\n\nHãy chia sẻ thông tin có ích, trao đổi lịch sự và kiểm tra tin phòng trước khi giao dịch. Nếu gặp nội dung không phù hợp, bạn có thể gửi báo cáo để được hỗ trợ.'
        elif role == 'LANDLORD':
            text += '\n\nMình là chủ trọ, sẵn sàng trao đổi rõ quy định và chi phí với người quan tâm. Thông tin tin phòng đính kèm chỉ có giá trị khi còn hiển thị công khai.'
        if status == 'PENDING':
            text += '\n\nMình thấy tin việc nhẹ lương cao trong nhóm nhưng chưa rõ đơn vị tuyển. Có ai biết cách kiểm tra thông tin trước khi liên hệ không?'
        if status == 'HIDDEN' and source == 'AUTO_RULE':
            text += '\n\nCó người nhắn câu “tao sẽ giết mày” trong cuộc tranh luận. Mình muốn hỏi cách báo cáo và xử lý tình huống này.'
        if status == 'RESTRICTED':
            text += '\n\nGiá tăng nhanh quá, vcl, mình phải cân đối lại ngân sách tháng này.'
        if status == 'REMOVED':
            text += '\n\nMình đăng lại thông tin này ở nhiều bài để mọi người dễ thấy, ai quan tâm cứ liên hệ nhé.'
        if number % 4 != 0:
            text += '\n\nMình lưu vài góc không gian nhiều cây xanh để tham khảo cách bố trí chỗ nghỉ ngơi.'
        if number % 17 == 0:
            text += '\nMọi người có thể đọc thêm kinh nghiệm trong mục Cẩm nang thuê trọ của Trọ Tốt.'
        text += f'\n\n{tags} #congdongthue'
        post_id = uid(f'post/{number}')
        age = 240 + (number - 1) * 101
        posts.append((post_id, number, role, text, area, status, version,
                      int(role == 'ADMIN' and status == 'VISIBLE' and number <= 60), age, deleted, source))
        if number % 4 != 0 and not deleted:
            for slot in range(1, 2 + number % 3):
                media.append((uid(f'media/{number}/{slot}'), post_id, IMAGES[(number + slot - 2) % 3]))
        # Pending/automatically hidden posts were never public and cannot have replies.
        if deleted or status in ('PENDING', 'RESTRICTED') or (status == 'HIDDEN' and source == 'AUTO_RULE'):
            continue
        roots = 24 if number % 25 == 0 else 4
        for root in range(1, roots + 1):
            index = number * 100 + root
            root_id = uid(f'comment/{number}/{root}')
            cstatus = {0: 'RESTRICTED', 1: 'PENDING', 2: 'HIDDEN', 3: 'REMOVED'}.get(index % 53, 'VISIBLE')
            cdeleted = int(index % 53 == 4)
            message = ROOT_COMMENTS[index % len(ROOT_COMMENTS)].format(area=area)
            if cstatus == 'RESTRICTED':
                message += ' Đọc giá mà bất ngờ vcl, chắc mình phải tìm thêm khu khác.'
            elif cstatus == 'PENDING':
                message += ' Có người giới thiệu việc nhẹ lương cao để kiếm thêm tiền trọ, không biết có đáng tin không?'
            elif cstatus == 'HIDDEN':
                message += ' Mình nhận được tin nhắn “tao sẽ giết mày” nên muốn nhờ quản trị viên hỗ trợ.'
            comments.append((root_id, post_id, None, index, message, cstatus, 1, root * 2, cdeleted))
            if cstatus != 'VISIBLE':
                continue
            for reply in range(1, 3):
                comments.append((uid(f'reply/{number}/{root}/{reply}'), post_id, root_id,
                                 index + reply * 13, REPLIES[(index + reply) % len(REPLIES)],
                                 'VISIBLE', 1, root * 2 + 2 + reply, 0))

    sql = ('''-- V12: Community demo data, MySQL 8.0.16+, UTF-8.
-- Generated by scripts/generate-community-seed.py during fresh scripts/setup-demo.ps1.
-- Flyway runs this once after V2 core demo data and V11 community schema.
-- Do not regenerate an applied migration; use a preview output or rebuild a fresh demo DB.
''' if migration else '''-- OPTIONAL DEMO PREVIEW, MySQL 8.0.16+, UTF-8.
-- Generated; execute only in a selected demo database after V11, in ONE connection.
''') + '''-- Does not create users/contracts/reviews or change credentials.
-- Uses existing ACTIVE seed identities listed in scripts/data/seed-accounts.json.
-- Existing posts/comments/cases are never overwritten. Missing demo interactions may be reinserted.
-- Do not use a client that continues after SQL errors: on error STOP and ROLLBACK.
-- Run while the demo app is idle; SQL seeding intentionally bypasses API rate limits.
-- External images are the three supplied resort images, not real listing photos.
-- Empty media public_id means an EXTERNAL image, not a Cloudinary-owned upload.
SET NAMES utf8mb4;
SET @community_seed_now = CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+07:00');
'''
    temp_names = ['identities', 'actors', 'authors', 'reporters', 'listings', 'posts', 'comments', 'media', 'numbers', 'guard']
    sql += ''.join(f'DROP TEMPORARY TABLE IF EXISTS tmp_community_seed_{name};\n' for name in temp_names)
    sql += '''CREATE TEMPORARY TABLE tmp_community_seed_identities(id varchar(36) PRIMARY KEY,role_code varchar(12))
 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
'''
    sql += values('tmp_community_seed_identities', ['id', 'role_code'], [(a['id'], a['role']) for a in accounts])
    sql += '''
CREATE TEMPORARY TABLE tmp_community_seed_actors AS
SELECT id, email, role_code, ROW_NUMBER() OVER(ORDER BY
 CASE WHEN email IN ('admin@gmail.com','landlord@gmail.com','tenant@gmail.com') THEN 0 ELSE 1 END,email,id) AS seq
FROM (
 SELECT u.id,u.email,i.role_code
 FROM tmp_community_seed_identities i JOIN users u ON u.id=i.id WHERE u.status='ACTIVE'
 AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
            WHERE ur.user_id=u.id AND r.code=i.role_code)
) candidates;
ALTER TABLE tmp_community_seed_actors ADD PRIMARY KEY(id), ADD UNIQUE KEY(seq);
CREATE TEMPORARY TABLE tmp_community_seed_authors AS
SELECT id,role_code,ROW_NUMBER() OVER(PARTITION BY role_code ORDER BY
 CASE WHEN email IN ('admin@gmail.com','landlord@gmail.com','tenant@gmail.com') THEN 0 ELSE 1 END,email,id) AS seq
FROM tmp_community_seed_actors;
ALTER TABLE tmp_community_seed_authors ADD PRIMARY KEY(role_code,seq);
CREATE TEMPORARY TABLE tmp_community_seed_reporters AS
SELECT a.id FROM tmp_community_seed_actors a JOIN users u ON u.id=a.id
WHERE u.email_verified_at IS NOT NULL AND u.created_at<=DATE_SUB(@community_seed_now,INTERVAL 24 HOUR);
CREATE TEMPORARY TABLE tmp_community_seed_listings AS
SELECT l.id,ROW_NUMBER() OVER(ORDER BY l.published_at DESC,l.id) AS seq
FROM listings l JOIN properties p ON p.id=l.property_id JOIN tmp_community_seed_actors a ON a.id=p.landlord_id
WHERE l.status='PUBLISHED' AND l.expires_at>@community_seed_now
AND NOT EXISTS(SELECT 1 FROM listings newer WHERE newer.property_id=l.property_id
 AND newer.status='PUBLISHED' AND newer.expires_at>@community_seed_now
 AND (newer.published_at>l.published_at OR (newer.published_at=l.published_at AND newer.id>l.id)));
SET @community_seed_actor_count=(SELECT COUNT(*) FROM tmp_community_seed_actors);
SET @community_seed_admin_count=(SELECT COUNT(*) FROM tmp_community_seed_authors WHERE role_code='ADMIN');
SET @community_seed_landlord_count=(SELECT COUNT(*) FROM tmp_community_seed_authors WHERE role_code='LANDLORD');
SET @community_seed_tenant_count=(SELECT COUNT(*) FROM tmp_community_seed_authors WHERE role_code='TENANT');
SET @community_seed_listing_count=(SELECT COUNT(*) FROM tmp_community_seed_listings);
SET @community_seed_admin=(SELECT id FROM tmp_community_seed_authors WHERE role_code='ADMIN' AND seq=1);
CREATE TEMPORARY TABLE tmp_community_seed_guard(ok int NOT NULL CHECK(ok=1));
-- Deliberately fails before persistent writes if the required demo actors are absent.
INSERT INTO tmp_community_seed_guard VALUES(IF(@community_seed_actor_count>=4
 AND @community_seed_admin_count>0 AND @community_seed_landlord_count>0 AND @community_seed_tenant_count>0
 AND (SELECT COUNT(*) FROM tmp_community_seed_reporters)>=4,1,0));
CREATE TEMPORARY TABLE tmp_community_seed_posts (
 id varchar(36) PRIMARY KEY, n int, author_role varchar(12), content text, area varchar(150),
 status varchar(24), version bigint, pinned boolean, age_minutes int, deleted boolean, source varchar(24)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TEMPORARY TABLE tmp_community_seed_comments (
 id varchar(36) PRIMARY KEY, post_id varchar(36), parent_id varchar(36), author_slot int, content text,
 status varchar(24), version bigint, delay_minutes int, deleted boolean
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TEMPORARY TABLE tmp_community_seed_media(id varchar(36) PRIMARY KEY,post_id varchar(36),url varchar(1000))
 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TEMPORARY TABLE tmp_community_seed_numbers(n int PRIMARY KEY);
'''
    sql += values('tmp_community_seed_numbers', ['n'], [(i,) for i in range(1, 61)])
    sql += values('tmp_community_seed_posts', ['id', 'n', 'author_role', 'content', 'area', 'status', 'version', 'pinned', 'age_minutes', 'deleted', 'source'], posts)
    sql += values('tmp_community_seed_comments', ['id', 'post_id', 'parent_id', 'author_slot', 'content', 'status', 'version', 'delay_minutes', 'deleted'], comments)
    sql += values('tmp_community_seed_media', ['id', 'post_id', 'url'], media)
    sql += '''
START TRANSACTION;
INSERT INTO community_posts(id,author_id,content,area,status,version,pinned,created_at,edited_at,deleted_at)
SELECT p.id,a.id,p.content,p.area,p.status,p.version,p.pinned,
 DATE_SUB(@community_seed_now,INTERVAL p.age_minutes MINUTE),
 IF(p.version>1,DATE_SUB(@community_seed_now,INTERVAL (p.age_minutes-90) MINUTE),NULL),
 IF(p.deleted,DATE_SUB(@community_seed_now,INTERVAL (p.age_minutes-120) MINUTE),NULL)
FROM tmp_community_seed_posts p JOIN tmp_community_seed_authors a
 ON a.role_code=p.author_role AND a.seq=IF(MOD(p.n,7)=0,1,1+MOD(p.n-1,
 CASE p.author_role WHEN 'ADMIN' THEN @community_seed_admin_count WHEN 'LANDLORD' THEN @community_seed_landlord_count ELSE @community_seed_tenant_count END))
LEFT JOIN community_posts existing ON existing.id=p.id WHERE existing.id IS NULL;
SET @community_seed_added_posts=ROW_COUNT();

INSERT INTO community_media(id,owner_id,post_id,url,public_id,created_at)
SELECT m.id,p.author_id,p.id,m.url,'',p.created_at
FROM tmp_community_seed_media m JOIN community_posts p ON p.id=m.post_id
JOIN tmp_community_seed_posts plan ON plan.id=p.id AND plan.version=p.version AND BINARY plan.content=BINARY p.content
LEFT JOIN community_media existing ON existing.id=m.id
WHERE existing.id IS NULL AND p.deleted_at IS NULL AND p.status=plan.status;
SET @community_seed_added_media=ROW_COUNT();

INSERT INTO community_post_listings(post_id,listing_id)
SELECT DISTINCT p.id,l.id FROM tmp_community_seed_posts plan JOIN community_posts p ON p.id=plan.id
JOIN tmp_community_seed_numbers slot ON slot.n<=1+MOD(FLOOR(plan.n/3),3)
JOIN tmp_community_seed_listings l ON l.seq=1+MOD(plan.n*7+slot.n,NULLIF(@community_seed_listing_count,0))
LEFT JOIN community_post_listings existing ON existing.post_id=p.id AND existing.listing_id=l.id
WHERE existing.post_id IS NULL AND MOD(plan.n,3)=0 AND p.deleted_at IS NULL
 AND p.version=plan.version AND BINARY p.content=BINARY plan.content AND p.status=plan.status;
SET @community_seed_added_links=ROW_COUNT();
'''
    for is_root in (True, False):
        sql += f'''
INSERT INTO community_comments(id,post_id,author_id,parent_id,content,status,version,created_at,deleted_at)
SELECT c.id,p.id,a.id,c.parent_id,c.content,c.status,c.version,
 DATE_ADD(p.created_at,INTERVAL c.delay_minutes MINUTE),
 IF(c.deleted,DATE_ADD(p.created_at,INTERVAL (c.delay_minutes+45) MINUTE),NULL)
FROM tmp_community_seed_comments c JOIN community_posts p ON p.id=c.post_id
JOIN tmp_community_seed_posts plan ON plan.id=p.id AND p.version=plan.version AND BINARY p.content=BINARY plan.content
JOIN tmp_community_seed_actors a ON a.seq=1+MOD(c.author_slot,@community_seed_actor_count)
{'LEFT JOIN community_comments root ON root.id=c.parent_id' if not is_root else ''}
LEFT JOIN community_comments existing ON existing.id=c.id
WHERE c.parent_id IS {'NULL' if is_root else 'NOT NULL'} AND existing.id IS NULL
 AND p.deleted_at IS NULL AND p.status=plan.status
{' AND root.id IS NOT NULL AND root.parent_id IS NULL AND root.post_id=p.id AND root.status=\'VISIBLE\'' if not is_root else ''};
SET @community_seed_added_{'roots' if is_root else 'replies'}=ROW_COUNT();
'''
    sql += '''
-- Reactions use actual actors, deterministic types, no reactions on hidden/deleted content.
INSERT INTO community_reactions(target_type,target_id,user_id,type)
SELECT DISTINCT t.target_type,t.id,a.id,ELT(1+MOD(CRC32(CONCAT(t.id,a.id)),3),'LIKE','LOVE','WOW')
FROM (
 SELECT 'POST' target_type,p.id,p.author_id,15+MOD(plan.n*7,46) quantity
 FROM community_posts p JOIN tmp_community_seed_posts plan ON plan.id=p.id
 WHERE p.status='VISIBLE' AND p.deleted_at IS NULL AND p.version=plan.version AND BINARY p.content=BINARY plan.content
 UNION ALL
 SELECT 'POST_COMMENT',c.id,c.author_id,3+MOD(CRC32(c.id),7)
 FROM community_comments c JOIN tmp_community_seed_comments cp ON cp.id=c.id
 JOIN community_posts p ON p.id=c.post_id
 LEFT JOIN community_comments parent ON parent.id=c.parent_id
 WHERE c.status='VISIBLE' AND c.deleted_at IS NULL AND c.version=cp.version AND BINARY c.content=BINARY cp.content
 AND p.status='VISIBLE' AND p.deleted_at IS NULL AND (c.parent_id IS NULL OR parent.status='VISIBLE')
) t
JOIN tmp_community_seed_numbers n ON n.n<=LEAST(t.quantity,@community_seed_actor_count)
JOIN tmp_community_seed_actors a ON a.seq=1+MOD(CRC32(t.id)+n.n,@community_seed_actor_count)
LEFT JOIN community_reactions existing ON existing.target_type=t.target_type AND existing.target_id=t.id AND existing.user_id=a.id
WHERE a.id<>t.author_id AND existing.target_id IS NULL;
SET @community_seed_added_reactions=ROW_COUNT();

INSERT INTO community_saved_posts(user_id,post_id,created_at)
SELECT DISTINCT a.id,p.id,DATE_ADD(p.created_at,INTERVAL 95 MINUTE)
FROM tmp_community_seed_posts plan JOIN community_posts p ON p.id=plan.id
JOIN tmp_community_seed_numbers n ON n.n<=6+MOD(plan.n,10)
JOIN tmp_community_seed_actors a ON a.seq=1+MOD(plan.n*11+n.n,@community_seed_actor_count)
LEFT JOIN community_saved_posts existing ON existing.user_id=a.id AND existing.post_id=p.id
WHERE existing.post_id IS NULL AND p.status IN ('VISIBLE','RESTRICTED') AND p.deleted_at IS NULL
 AND p.version=plan.version AND BINARY p.content=BINARY plan.content;
SET @community_seed_added_saves=ROW_COUNT();

INSERT INTO community_follows(follower_id,followee_id)
SELECT DISTINCT a.id,p.author_id
FROM tmp_community_seed_actors a JOIN tmp_community_seed_numbers n ON n.n<=10
JOIN tmp_community_seed_posts plan ON plan.n=1+MOD(a.seq*13+n.n*17,''' + str(post_count) + ''')
JOIN community_posts p ON p.id=plan.id
LEFT JOIN community_follows existing ON existing.follower_id=a.id AND existing.followee_id=p.author_id
WHERE a.id<>p.author_id AND existing.follower_id IS NULL;
SET @community_seed_added_follows=ROW_COUNT();
'''
    report_id = sql_uuid("CONCAT('community-seed-report/',r.target_id,'/',r.reporter_id)")
    sql += f'''
INSERT INTO content_reports(id,target_type,target_id,content_version,reporter_id,reason,created_at)
SELECT {report_id},'POST',r.target_id,r.version,r.reporter_id,
 'Thông tin trong bài chưa rõ nguồn, đề nghị kiểm tra trước khi tiếp tục hiển thị.',
 DATE_ADD(r.created_at,INTERVAL (95+r.report_order) MINUTE)
FROM (
 SELECT p.id target_id,p.author_id,p.version,p.created_at,a.id reporter_id,
 ROW_NUMBER() OVER(PARTITION BY p.id ORDER BY CRC32(CONCAT(p.id,a.id)),a.id) report_order
 FROM community_posts p JOIN tmp_community_seed_posts plan ON plan.id=p.id
 JOIN tmp_community_seed_reporters a ON a.id<>p.author_id
 WHERE plan.source='USER_REPORT' AND p.status='HIDDEN' AND p.deleted_at IS NULL
 AND p.version=plan.version AND BINARY p.content=BINARY plan.content
) r LEFT JOIN content_reports existing ON existing.target_type='POST' AND existing.target_id=r.target_id
 AND existing.content_version=r.version AND existing.reporter_id=r.reporter_id
WHERE r.report_order<=3 AND existing.id IS NULL;
SET @community_seed_added_reports=ROW_COUNT();
'''
    case_id = sql_uuid("CONCAT('community-seed-case/',t.target_type,'/',t.id)")
    sql += f'''
INSERT INTO content_moderation_cases(id,target_type,target_id,author_id,content_version,snapshot,reason,status,source,resolved,handled_by,resolution_note,created_at)
SELECT {case_id},t.target_type,t.id,t.author_id,t.version,t.content,
 CASE WHEN t.source='USER_REPORT' THEN 'Có 3 thành viên báo cáo; nội dung được tạm ẩn để xác minh.'
 WHEN t.status='RESTRICTED' THEN 'Nội dung có ngôn từ không phù hợp, tạm hạn chế hiển thị.'
 WHEN t.status='PENDING' THEN 'Thông tin tuyển dụng cần được xem xét thêm về nguồn và ngữ cảnh.'
 WHEN t.status='HIDDEN' THEN 'Nội dung có câu đe dọa, cần được quản trị viên xem xét.'
 ELSE 'Bài quảng cáo được đăng lặp nhiều lần, đã gỡ sau khi xem xét.' END,
 t.status,t.source,t.status='REMOVED',IF(t.status='REMOVED',@community_seed_admin,NULL),
 IF(t.status='REMOVED','Đã kiểm tra bằng chứng và quyết định gỡ nội dung.',NULL),DATE_ADD(t.created_at,INTERVAL 110 MINUTE)
FROM (
 SELECT 'POST' target_type,p.id,p.author_id,p.version,p.content,p.status,plan.source,p.created_at
 FROM community_posts p JOIN tmp_community_seed_posts plan ON plan.id=p.id
 WHERE p.status IN ('RESTRICTED','PENDING','HIDDEN','REMOVED') AND p.status=plan.status
 AND p.deleted_at IS NULL AND p.version=plan.version AND BINARY p.content=BINARY plan.content
 UNION ALL
 SELECT 'POST_COMMENT',c.id,c.author_id,c.version,c.content,c.status,IF(c.status='REMOVED','MANUAL','AUTO_RULE'),c.created_at
 FROM community_comments c JOIN tmp_community_seed_comments cp ON cp.id=c.id
 WHERE c.status IN ('RESTRICTED','PENDING','HIDDEN','REMOVED') AND c.status=cp.status
 AND c.deleted_at IS NULL AND c.version=cp.version AND BINARY c.content=BINARY cp.content
) t LEFT JOIN content_moderation_cases existing ON existing.id={case_id}
WHERE existing.id IS NULL;
SET @community_seed_added_cases=ROW_COUNT();
'''
    appeal_id = sql_uuid("CONCAT('community-seed-appeal/',p.id)")
    action_id = sql_uuid("CONCAT('community-seed-action/',c.id)")
    sql += f'''
INSERT INTO content_moderation_cases(id,target_type,target_id,author_id,content_version,snapshot,reason,status,source,resolved,created_at)
SELECT {appeal_id},'POST',p.id,p.author_id,p.version,p.content,
 'Mình đang trích lại câu nói trong cuộc trao đổi để hỏi cách xử lý, mong quản trị viên xem lại ngữ cảnh.',p.status,'APPEAL',false,
 DATE_ADD(p.created_at,INTERVAL 120 MINUTE)
FROM community_posts p JOIN tmp_community_seed_posts plan ON plan.id=p.id
LEFT JOIN content_moderation_cases existing ON existing.id={appeal_id}
WHERE existing.id IS NULL AND p.status='RESTRICTED' AND MOD(plan.n,60)=2 AND p.status=plan.status
 AND p.deleted_at IS NULL AND p.version=plan.version AND BINARY p.content=BINARY plan.content;
SET @community_seed_added_appeals=ROW_COUNT();

INSERT INTO moderation_actions(id,actor_id,target_id,action,reason,created_at)
SELECT {action_id},@community_seed_admin,c.target_id,'CONTENT_REMOVED',
 'Gỡ nội dung quảng cáo lặp sau khi kiểm tra báo cáo.',DATE_ADD(c.created_at,INTERVAL 1 MINUTE)
FROM content_moderation_cases c
LEFT JOIN moderation_actions existing ON existing.id={action_id}
WHERE existing.id IS NULL AND c.resolved=true AND c.status='REMOVED' AND c.handled_by=@community_seed_admin
 AND c.resolution_note='Đã kiểm tra bằng chứng và quyết định gỡ nội dung.'
 AND c.id IN (
 SELECT {sql_uuid("CONCAT('community-seed-case/POST/',p.id)")} FROM tmp_community_seed_posts p WHERE p.status='REMOVED'
 UNION ALL SELECT {sql_uuid("CONCAT('community-seed-case/POST_COMMENT/',cp.id)")} FROM tmp_community_seed_comments cp WHERE cp.status='REMOVED'
);
SET @community_seed_added_actions=ROW_COUNT();
COMMIT;

SELECT @community_seed_added_posts added_posts,@community_seed_added_media added_images,
 @community_seed_added_links added_listing_links,@community_seed_added_roots added_root_comments,
 @community_seed_added_replies added_replies,@community_seed_added_reactions added_reactions,
 @community_seed_added_saves added_saved_posts,@community_seed_added_follows added_follows,
 @community_seed_added_reports added_reports,@community_seed_added_cases added_cases,
 @community_seed_added_appeals added_appeals,@community_seed_added_actions added_audit_actions;
SELECT status,COUNT(*) seeded_posts FROM community_posts p JOIN tmp_community_seed_posts plan ON plan.id=p.id GROUP BY status;
SELECT @community_seed_actor_count available_seed_accounts,@community_seed_listing_count available_public_listings;
'''
    # Qualify status in the final count: both the actual and planned tables have it.
    sql = sql.replace('SELECT status,COUNT(*) seeded_posts', 'SELECT p.status,COUNT(*) seeded_posts').replace('GROUP BY status;', 'GROUP BY p.status;')
    sql += ''.join(f'DROP TEMPORARY TABLE IF EXISTS tmp_community_seed_{name};\n' for name in reversed(temp_names))
    return sql, {'posts': len(posts), 'comments': len(comments), 'images': len(media), 'post_statuses': dict(Counter(p[5] for p in posts))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--posts', type=int, default=600)
    parser.add_argument('--output', type=Path, default=SEED)
    parser.add_argument('--accounts', type=Path, default=ROOT / 'scripts/data/seed-accounts.json')
    args = parser.parse_args()
    if not 60 <= args.posts <= 10000:
        parser.error('--posts must be between 60 and 10000')
    accounts = json.loads(args.accounts.read_text(encoding='utf-8'))
    sql, summary = build(args.posts, migration=args.output.resolve() == SEED.resolve(), accounts=accounts)
    args.output.write_text(sql, encoding='utf-8', newline='\n')
    counts_path = (ROOT / 'scripts/data/community-counts.json' if args.output.resolve() == SEED.resolve()
                   else args.output.with_suffix('.counts.json'))
    counts_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'Generated {args.output}: {summary}')


if __name__ == '__main__':
    main()
