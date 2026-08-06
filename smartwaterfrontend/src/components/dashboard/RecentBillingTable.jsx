import "./RecentBillingTable.css";

export default function RecentBillingTable({ cycles }) {
  return (
    <div className="billing-table">

      <h3>Recent Billing Cycles</h3>

      <table>
        <thead>
          <tr>
            <th>Cycle</th>
            <th>Status</th>
            <th>Invoices</th>
          </tr>
        </thead>

      <tbody>
  {cycles.length === 0 ? (
    <tr>
      <td colSpan="3" style={{ textAlign: "center", padding: "20px" }}>
        No billing cycles found.
      </td>
    </tr>
  ) : (
    cycles.map((cycle) => (
      <tr key={cycle.id}>
        <td>
          {cycle.cycleStartDate} - {cycle.cycleEndDate}
        </td>

        <td>{cycle.status}</td>

        <td>{cycle.id}</td>
      </tr>
    ))
  )}
</tbody>

      </table>

    </div>
  );
}