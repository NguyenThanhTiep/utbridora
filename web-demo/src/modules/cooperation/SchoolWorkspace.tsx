export function SchoolWorkspace() {
  return <div className="stack">
    <section className="panel"><h2>Hợp tác với doanh nghiệp</h2>
      <p>Không gian dành cho cán bộ nhà trường theo dõi nhu cầu và phối hợp thực tập.</p>
      <p role="status">Đang chờ tích hợp API theo phạm vi nhà trường. Chưa có dữ liệu nghiệp vụ để hiển thị.</p>
    </section>
    {['Xác minh doanh nghiệp', 'Nhu cầu và đề nghị hợp tác'].map(title =>
      <section className="panel" key={title}><h2>{title}</h2>
        <p>Đang chờ tích hợp. Các thao tác xem hồ sơ và xử lý sẽ được mở khi dịch vụ sẵn sàng và bạn có quyền phù hợp.</p>
      </section>)}
  </div>;
}
