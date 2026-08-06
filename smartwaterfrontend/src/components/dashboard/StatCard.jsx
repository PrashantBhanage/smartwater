import "./StatCard.css";

export default function StatCard({ title, value, subtitle, icon }) {
  return (
    <div className="stat-card">
      <div className="stat-card__top">
        <div>
          <p className="stat-card__title">{title}</p>
          <h2 className="stat-card__value">{value}</h2>
        </div>

        {icon && <div className="stat-card__icon">{icon}</div>}
      </div>

      {subtitle && (
        <p className="stat-card__subtitle">{subtitle}</p>
      )}
    </div>
  );
}