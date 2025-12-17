import './ManagementScreen.css';

interface ManagementScreenProps {
    onBack: () => void;
    onNavigateToFavourites: () => void;
    onNavigateToViewedQuotes: () => void;
    onNavigateToUserManagement: () => void;
    hasUserRole: boolean;
    hasAdminRole: boolean;
}

export function ManagementScreen({
    onBack,
    onNavigateToFavourites,
    onNavigateToViewedQuotes,
    onNavigateToUserManagement,
    hasUserRole,
    hasAdminRole,
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
                <button
                    className="management-menu-item"
                    onClick={onNavigateToUserManagement}
                    disabled={!hasAdminRole}
                    title={!hasAdminRole ? 'ADMIN role required' : ''}
                >
                    User Management
                </button>
            </div>
            {!hasUserRole && (
                <p className="role-warning">USER role required to access favourites and viewed quotes.</p>
            )}
            {!hasAdminRole && (
                <p className="role-warning">ADMIN role required to access user management.</p>
            )}
        </div>
    );
}
