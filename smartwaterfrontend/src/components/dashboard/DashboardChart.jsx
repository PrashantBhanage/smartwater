import {
  ResponsiveContainer,
  BarChart,
  Bar,
  CartesianGrid,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import "./DashboardChart.css";

export default function DashboardChart({ data }) {
  return (
    <div className="dashboard-chart">
      <div className="dashboard-chart__header">
        <h3>Billing Cycles</h3>
        <span>Current Overview</span>
      </div>

      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />

          <XAxis dataKey="name" />

          <YAxis />

          <Tooltip />

          <Bar
  dataKey="value"
  fill="#000000"
  radius={[8, 8, 0, 0]}
/>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}