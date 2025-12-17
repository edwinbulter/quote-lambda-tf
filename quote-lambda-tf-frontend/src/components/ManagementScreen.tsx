import './ManagementScreen.css';

interface ManagementScreenProps {
    onBack: () => void;
    onNavigateToFavourites: () => void;
    onNavigateToViewedQuotes: () => void;
    hasUserRole: boolean;
}

export function ManagementScreen({
    onBack,
    onNavigateToFavourites,
    onNavigateToViewedQuotes,
    hasUserRole,
}: ManagementScreenProps) {
    return (
        <div className="management-screen">
            <div className="management-header">
                <button className="back-button" onClick={onBack}>
                    ← Back
                </button>
                <h2>Management</h2>
            </div>
            <div className="management-menu">
                <button
                    className="management-menu-item"
                    onClick={onNavigateToFavourites}
                    disabled={!hasUserRole}
                    title={!hasUserRole ? 'USER role required' : ''}
                >
                    Manage Favourites
                </button>
                <button
                    className="management-menu-item"
                    onClick={onNavigateToViewedQuotes}
                    disabled={!hasUserRole}
                    title={!hasUserRole ? 'USER role required' : ''}
                >
                    Viewed Quotes
                </button>
            </div>
            {!hasUserRole && (
                <p className="role-warning">USER role required to access these features.</p>
            )}
        </div>
    );
}
