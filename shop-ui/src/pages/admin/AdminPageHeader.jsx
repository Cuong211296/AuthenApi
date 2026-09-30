/** Section header of an admin page: h1 (display font), optional description and right-aligned actions. */
export default function AdminPageHeader({ title, description, actions }) {
  return (
    <header className="ad-head">
      <div className="ad-head__text">
        <h1 className="ad-head__title">{title}</h1>
        {description && <p className="ad-head__desc">{description}</p>}
      </div>
      {actions && <div className="ad-head__actions">{actions}</div>}
    </header>
  );
}
