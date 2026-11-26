# Quy ước format và refactor code backend

## 1. Mục tiêu và phạm vi

Code phải dễ đọc ngay khi mở file: phân biệt được annotation, khai báo, điều kiện và từng bước xử lý. Không nén code thành một dòng để giảm số dòng.

- Áp dụng cho toàn bộ code backend do dự án quản lý: `src/main`, `src/test`, `scripts` và các file cấu hình, tài liệu liên quan trong `DOANTOTNGHIEPBE`.
- Không áp dụng cho frontend, thư viện bên ngoài, file sinh tự động, `target`, Maven Wrapper, lockfile hoặc tài nguyên nhị phân.
- Không format lại migration Flyway đã chạy vì thay đổi nội dung có thể làm sai checksum. Quy ước SQL áp dụng cho migration mới và script được phép chỉnh sửa.
- Tài liệu này quy định cách trình bày code. Các quy tắc kiến trúc và nghiệp vụ tiếp tục theo [RULE.md](RULE.md), [BUSINESS.md](BUSINESS.md) và [api.md](api.md).
- Khi được yêu cầu refactor theo tài liệu này, giữ nguyên hành vi; tách các đề xuất thay đổi nghiệp vụ hoặc kiến trúc thành việc riêng.

## 2. Thụt lề, độ dài dòng và khoảng trắng

- Java: dùng **4 dấu cách** cho mỗi cấp thụt lề, không dùng tab.
- Dòng tiếp nối trong Java: thụt thêm **4 dấu cách** so với dòng bắt đầu biểu thức hoặc khai báo.
- Độ dài dòng Java mục tiêu: **tối đa 120 ký tự**, tính cả thụt lề. Chủ động xuống dòng sớm khi nhiều tham số hoặc điều kiện làm code khó đọc.
- Không bẻ URL, regex, chuỗi nội dung, SQL literal hoặc dữ liệu chỉ để đạt giới hạn dòng nếu việc đó có thể thay đổi giá trị.
- Một dòng chỉ chứa một câu lệnh. Không viết nhiều khai báo biến trên cùng một dòng.
- Có một dấu cách quanh toán tử: `=`, `==`, `!=`, `+`, `&&`, `||`, `?`, `:`; không thêm khoảng trắng trong `List<Post>` hoặc trước/sau toán tử truy cập `.`.
- Có một dấu cách sau dấu phẩy; không có dấu cách trước dấu phẩy hoặc dấu chấm phẩy.
- Viết `if (...)`, `for (...)`, `while (...)`, `switch (...)`, `catch (...)`; lời gọi hàm viết `service.feed(...)`.
- Viết `{` cùng dòng với khai báo hoặc điều kiện, cách một dấu cách. Viết `}` trên dòng riêng; dùng `} else {`, `} catch (...) {`, `} finally {`.
- Không căn thẳng dấu `=` hoặc tên biến bằng nhiều dấu cách; dùng thụt lề theo cấp.
- Không để khoảng trắng thừa cuối dòng. File văn bản dùng UTF-8 và có đúng một ký tự xuống dòng kết thúc file.
- Giữ kiểu xuống dòng hiện có của file để tránh diff toàn file chỉ vì CRLF/LF. File mới dùng LF, trừ khi cấu hình repository quy định khác.

## 3. Dòng trống: cách nhau bao nhiêu là đủ?

- Sau `package`: **1 dòng trống** trước nhóm `import`.
- Giữa các nhóm `import`: **1 dòng trống**; sau nhóm cuối: **1 dòng trống** trước annotation/class.
- Giữa hai method hoặc constructor: **1 dòng trống**.
- Giữa các nhóm constant, dependency, field, constructor, method: **1 dòng trống**.
- Các field đơn giản cùng mục đích đặt liên tiếp, mỗi field một dòng. Các field có annotation hoặc thuộc nhóm khác nhau cách nhau **1 dòng trống**.
- Annotation gắn liền với khai báo: mỗi annotation một dòng, không chèn dòng trống giữa annotation và class/method/field.
- Trong method, dùng **1 dòng trống** để ngăn các bước khác nhau, ví dụ: kiểm tra đầu vào → lấy dữ liệu → cập nhật → trả kết quả.
- Các câu lệnh liên quan trực tiếp đặt liên tiếp. Không chèn dòng trống sau mọi câu lệnh.
- Không có từ **2 dòng trống liên tiếp** trong source code.
- Không chèn dòng trống ngay sau `{` hoặc ngay trước `}`.

Ví dụ tách các bước xử lý:

```java
public void delete(String actor, String type, String id) {
    validateDeleteRequest(actor, type, id);

    Post post = findPost(id);
    checkOwnership(actor, post);

    repository.delete(post);
}
```

Đây là ví dụ cách trình bày, không phải yêu cầu bổ sung các hàm hoặc thay đổi luồng xóa hiện tại.

## 4. Method, annotation và tham số Java

- Method và constructor có thân xử lý phải mở thành nhiều dòng, kể cả khi chỉ có một lệnh `return`.
- Không đặt annotation của class/method/field cùng dòng với khai báo. Annotation của tham số có thể cùng dòng với tham số đó.
- Method có từ **3 tham số trở lên**, hoặc chữ ký vượt 120 ký tự: xuống dòng, mỗi tham số một dòng, dấu `)` và `{` đặt thành `) {` trên dòng riêng.
- Với 0–2 tham số ngắn, chữ ký có thể nằm trên một dòng; thân method vẫn xuống dòng.
- Annotation có nhiều thuộc tính dài: mỗi thuộc tính một dòng, dấu `)` trên dòng riêng.
- Lời gọi hàm có thể giữ trên một dòng khi ngắn, rõ nghĩa và không vượt 120 ký tự. Khi cần tách, mỗi đối số một dòng, dấu `);` trên dòng riêng.
- Method abstract/interface kết thúc bằng `;`; không tạo thân rỗng. Block thực sự rỗng có thể dùng `{}`, không tự thêm log hoặc exception chỉ để lấp chỗ trống.

### Ví dụ trực tiếp từ CommunityController

Không viết:

```java
private String actor(Principal p){return p==null?null:p.getName();}
@GetMapping("/listings") public ApiResponse<java.util.List<ListingLink>> listings(@RequestParam(defaultValue="") String q){return ApiResponse.success(service.listingChoices(q));}
@GetMapping("/feed") public ApiResponse<Slice<Post>> feed(Principal p,@RequestParam(defaultValue="LATEST") String tab,@RequestParam(required=false) String cursor,@RequestParam(required=false) String q){return ApiResponse.success(service.feed(actor(p),tab,cursor,q));}
```

Viết thành:

```java
package com.tamdaptit.rentalmanagement.community.controller;

import java.security.Principal;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.ListingLink;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Post;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Slice;
import com.tamdaptit.rentalmanagement.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityService service;

    @GetMapping("/listings")
    public ApiResponse<List<ListingLink>> listings(@RequestParam(defaultValue = "") String q) {
        return ApiResponse.success(service.listingChoices(q));
    }

    @GetMapping("/feed")
    public ApiResponse<Slice<Post>> feed(
        Principal p,
        @RequestParam(defaultValue = "LATEST") String tab,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String q
    ) {
        return ApiResponse.success(service.feed(actor(p), tab, cursor, q));
    }

    private String actor(Principal p) {
        return p == null ? null : p.getName();
    }
}
```

Ví dụ chỉ trích hai endpoint và helper liên quan, không thay thế toàn bộ controller. Các tên `p`, `q`, giá trị mặc định và lời gọi service được giữ nguyên để minh họa việc format.

## 5. Điều kiện, vòng lặp và biểu thức dài

- Luôn dùng `{}` cho `if`, `else`, `for`, `while`, `do`, kể cả khi chỉ có một câu lệnh.
- Không viết `if (...) return ...;`, `for (...) service.save(...);` hoặc gộp toàn bộ `try/catch` trên một dòng.
- Điều kiện dài xuống dòng; mỗi điều kiện con một dòng, đặt `&&`/`||` cuối dòng trước. Giữ nguyên dấu ngoặc và thứ tự đánh giá.
- Ternary đơn giản có thể nằm trên một dòng, ví dụ `p == null ? null : p.getName()`. Không viết ternary lồng nhau; chỉ chuyển thành `if/else` khi bảo toàn kiểu và hành vi biểu thức.
- Stream, builder hoặc chain dài: mỗi bước trên một dòng, dấu `.` ở đầu dòng tiếp nối.
- Lambda chỉ trả về một biểu thức ngắn có thể viết `post -> post.id()`. Lambda có nhiều câu lệnh phải có block nhiều dòng.
- Không chuyển vòng lặp sang stream, đổi thứ tự filter/map hoặc đổi thứ tự điều kiện chỉ để code trông ngắn hơn.

```java
if (
    principal == null ||
    cursor == null ||
    cursor.isBlank()
) {
    return;
}

List<String> ids = posts.stream()
    .map(Post::id)
    .toList();
```

## 6. Import, thứ tự thành phần và tên

- Không dùng wildcard import (`import ...*`), kể cả static import; thay bằng các import cụ thể được sử dụng.
- Import theo thứ tự: `java.*`/`javax.*` → các package còn lại → static import. Trong mỗi nhóm, sắp xếp theo tên đầy đủ.
- Dùng import thay vì viết `java.util.List` rải rác trong chữ ký. Giữ tên đầy đủ khi cần phân biệt các type trùng tên.
- Xóa import trùng hoặc không còn dùng sau thay đổi. Không nhân tiện xóa method, field hoặc class chỉ vì thấy có vẻ không được dùng.
- Thứ tự khai báo ưu tiên cho code mới: constant → dependency/field → constructor → public method → protected/package-private method → private helper → nested type.
- Với file hiện có, ưu tiên tách dòng tại chỗ; chỉ di chuyển thành phần khi cần và không làm thay đổi thứ tự khởi tạo field/static block.
- Class/interface/record dùng `PascalCase`; method và biến dùng `camelCase`; constant dùng `UPPER_SNAKE_CASE`; package viết thường theo cấu trúc domain hiện tại.
- Code mới dùng tên có nghĩa như `principal`, `request`, `query`. Không đổi tên hàng loạt trong một lượt format.
- Không tự đổi tên tham số được Spring suy luận thành query/path/part name. Nếu đổi tên biến, phải bảo toàn tên binding rõ ràng, ví dụ `@RequestParam(name = "q") String query`.
- Không đổi tên field DTO/entity, JSON property, enum, cột hoặc bảng database trong một lượt format.

## 7. Áp dụng ở từng lớp backend

- **Controller:** annotation mapping ở dòng riêng; tham số theo mục 4; các endpoint cách nhau một dòng trống. Giữ nguyên route, HTTP method, consumes/produces, binding và response.
- **Service/implementation:** `@Override`, `@Transactional`, `@Service` ở dòng riêng; phân tách các bước bằng dòng trống. Giữ nguyên transaction boundary, quyền truy cập, khóa và thứ tự gọi hệ thống bên ngoài.
- **Repository:** mỗi method một khai báo riêng; tách annotation/query dài cho dễ đọc. Không đổi câu query, loại fetch hoặc tham số binding trong lượt format.
- **Entity/DTO/record/enum:** mỗi field hoặc enum constant một dòng; annotation gắn với field; record dài tách từng component như tham số method. Giữ nguyên mapping, annotation validation và kiểu dữ liệu.
- **Mapper/config/job/adapter:** áp dụng cùng quy tắc Java; không tạo thêm lớp/interface hoặc di chuyển package chỉ để format.
- **Test:** dùng cùng quy tắc Java; có thể cách một dòng trống giữa phần chuẩn bị, thực thi và kiểm tra. Giữ nguyên assertion và dữ liệu kiểm thử.
- **Comment/Javadoc:** viết rõ lý do hoặc ràng buộc cần biết; comment đặt ngay trước đoạn liên quan. Không thêm comment lặp lại điều code đã nói rõ.
- Không xóa comment về transaction, concurrency, bảo mật hoặc nghiệp vụ chỉ để file trông ngắn hơn.

## 8. Các file khác trong BE

- **YAML/YML:** thụt 2 spaces, không dùng tab, mỗi key một dòng; giữ nguyên nesting và nội dung scalar nhiều dòng.
- **JSON:** thụt 2 spaces, mỗi thuộc tính một dòng với object nhiều thuộc tính; không thêm comment hoặc trailing comma.
- **XML/POM/JRXML:** thụt 2 spaces theo cấp; phần tử con xuống dòng. Không thay đổi nội dung text, CDATA, biểu thức báo cáo hoặc thứ tự phần tử có ý nghĩa.
- **Properties/env/conf:** mỗi cấu hình một dòng; giữ cú pháp, escaping, khoảng trắng thuộc giá trị và thứ tự override. Không tự đổi dấu nháy hoặc sắp xếp lại các key trùng nhau.
- **SQL mới/script SQL được phép sửa:** viết hoa keyword, mỗi clause chính (`SELECT`, `FROM`, `WHERE`, `JOIN`, `ORDER BY`) một dòng; thụt phần tiếp nối 4 spaces. Giữ nguyên literal, thứ tự lệnh và dữ liệu seed.
- **JavaScript/MJS:** thụt 2 spaces; block nhiều dòng, một câu lệnh một dòng, method cách nhau một dòng trống. Giữ nguyên cú pháp module và nội dung chuỗi.
- **Python/PowerShell:** thụt 4 spaces; tách khối xử lý bằng một dòng trống. Đặc biệt giữ đúng scope Python, backtick tiếp nối PowerShell và nội dung multiline string/here-string.
- **Markdown:** một dòng trống giữa heading và nội dung, trước/sau danh sách và code block; gắn ngôn ngữ cho code fence. Không áp giới hạn 120 ký tự cho URL hoặc ép ngắt dòng văn bản.
- Với loại file chưa liệt kê, tuân thủ formatter và cú pháp hiện có của loại file đó; không áp quy tắc dấu ngoặc Java một cách máy móc.
- Không format nội dung nhúng như SQL/HTML/JSON trong Java string, text block hoặc fixture nếu khoảng trắng có thể là một phần của dữ liệu.

## 9. Quy trình refactor theo tài liệu này

1. Xác định file/module cần xử lý và ghi nhận các thay đổi đang có; không ghi đè công việc chưa hoàn thành của người khác.
2. Kiểm tra cấu hình formatter hiện có. Nếu formatter làm trái quy tắc này, điều chỉnh profile trong phạm vi được giao hoặc xử lý phần đó thủ công; không để formatter và chỉnh tay ghi đè qua lại.
3. Xử lý từng module: controller → service → repository/mapper → DTO/entity → config/job/test và script liên quan.
4. Ưu tiên thay đổi trình bày: xuống dòng, thụt lề, khoảng trắng, annotation và import. Việc đổi tên, tách hàm hoặc đơn giản hóa logic cần được xem xét riêng.
5. Soát diff để chắc chắn không đổi chuỗi, binding, giá trị mặc định, null handling, thứ tự xử lý, query, transaction, mapping hoặc quyền truy cập.
6. Kiểm tra biên dịch và chạy các kiểm thử liên quan đang có. Nếu đã có lỗi trước khi format, ghi nhận lỗi nền và phân biệt với lỗi do thay đổi.
7. Báo rõ phạm vi đã sửa và kết quả kiểm tra thực tế; không khẳng định toàn bộ BE đã đạt convention khi chưa kiểm tra hết.

Chạy từ thư mục `DOANTOTNGHIEPBE` trên Windows:

```powershell
# Kiểm tra khoảng trắng trong diff của các file đã được Git theo dõi.
git diff --check

# Kiểm tra biên dịch mã nguồn chính.
.\mvnw.cmd -DskipTests compile

# Chạy test hiện có khi thay đổi ảnh hưởng đến code được kiểm thử.
.\mvnw.cmd test
```

`git diff --check` không kiểm tra toàn bộ convention và không bao gồm file chưa được Git theo dõi. Maven compile/test cũng không thay thế việc đọc diff; không cần thêm test chỉ để kiểm tra thay đổi khoảng trắng.

## 10. Checklist hoàn thành

- [ ] Không còn method có thân xử lý bị nén trên một dòng trong phạm vi đã sửa.
- [ ] Annotation của class/method/field ở dòng riêng và sát khai báo.
- [ ] Java thụt 4 spaces, không dùng tab; dòng dài đã được tách hợp lý.
- [ ] Method/constructor cách nhau đúng một dòng trống; không có hai dòng trống liên tiếp.
- [ ] Các khối điều kiện và vòng lặp dùng `{}`; mỗi dòng một câu lệnh.
- [ ] Tham số, biểu thức dài, stream và builder được trình bày nhất quán.
- [ ] Import cụ thể, đúng nhóm; không còn import thừa do thay đổi.
- [ ] Không thay đổi API, dữ liệu, logic, mapping, transaction hoặc thứ tự khởi tạo.
- [ ] Không sửa file sinh tự động hoặc migration đã chạy.
- [ ] Diff đã được xem lại; kết quả compile/test liên quan đã được ghi nhận.

> Tiêu chí chính: đọc được cấu trúc và từng bước xử lý mà không phải tự tách các câu lệnh đang dồn trên cùng một dòng.
