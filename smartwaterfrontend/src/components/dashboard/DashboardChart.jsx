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

export default function DashboardChart({
  data,
  title = "Household Usage Comparison",
  subtitle = "Current Period Volume (Liters)",
}) {
  return (
    <div className="dashboard-chart">
      <div className="dashboard-chart__header">
        <h3>{title}</h3>
        <span>{subtitle}</span>
      </div>

      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--sw-border, #E5E7EB)" />
          <XAxis dataKey="name" stroke="var(--sw-text-secondary, #6B7280)" fontSize={12} />
          <YAxis stroke="var(--sw-text-secondary, #6B7280)" fontSize={12} unit=" L" />
          <Tooltip
            contentStyle={{
              background: 'var(--sw-surface-raised, #FFF)',
              border: '1px solid var(--sw-border, #E5E7EB)',
              borderRadius: 'var(--sw-radius, 8px)'
            }}
          />
          <Bar
            dataKey="value"
            name="Usage (L)"
            fill="var(--sw-accent, #1A73E8)"
            radius={[8, 8, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}