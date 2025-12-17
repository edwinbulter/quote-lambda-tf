import { useEffect, useState } from 'react';
import './UserManagementScreen.css';
import adminApi, { UserInfo } from '../api/adminApi';
import { Toast } from './Toast';
import { useAuth } from '../contexts/AuthContext';

interface UserManagementScreenProps {
    onBack: () => void;
}

export function UserManagementScreen({ onBack }: UserManagementScreenProps) {
    const [users, setUsers] = useState<UserInfo[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
    const { user } = useAuth();

    useEffect(() => {
        loadUsers();
    }, []);

    const loadUsers = async () => {
        try {
            setLoading(true);
            const userList = await adminApi.listUsers();
            setUsers(userList);
        } catch (error) {
            console.error('Failed to load users:', error);
            showToast('Failed to load users', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleToggleRole = async (username: string, groupName: string, currentlyInGroup: boolean) => {
        if (groupName === 'ADMIN' && username === user?.username && currentlyInGroup) {
            showToast('Cannot remove yourself from ADMIN group', 'error');
            return;
        }

        const previousUsers = [...users];
        
        const updatedUsers = users.map(u => {
            if (u.username === username) {
                const newGroups = currentlyInGroup
                    ? u.groups.filter(g => g !== groupName)
                    : [...u.groups, groupName];
                return { ...u, groups: newGroups };
            }
            return u;
        });
        setUsers(updatedUsers);

        try {
            if (currentlyInGroup) {
                await adminApi.removeUserFromGroup(username, groupName);
                showToast(`Removed ${username} from ${groupName}`, 'success');
            } else {
                await adminApi.addUserToGroup(username, groupName);
                showToast(`Added ${username} to ${groupName}`, 'success');
            }
        } catch (error) {
            console.error('Failed to update role:', error);
            setUsers(previousUsers);
            showToast('Failed to update role', 'error');
        }
    };

    const showToast = (message: string, type: 'success' | 'error') => {
        setToast({ message, type });
    };

    return (
        <div className="user-management-screen">
            <div className="user-management-header">
                <button className="back-button" onClick={onBack}>
                    ← Back
                </button>
                <h2>User Management</h2>
            </div>

            {loading ? (
                <div className="loading">Loading users...</div>
            ) : users.length === 0 ? (
                <div className="empty-state">No users found.</div>
            ) : (
                <div className="users-table-container">
                    <table className="users-table">
                        <thead>
                            <tr>
                                <th>Username</th>
                                <th>Email</th>
                                <th>USER Role</th>
                                <th>ADMIN Role</th>
                            </tr>
                        </thead>
                        <tbody>
                            {users.map((userInfo) => {
                                const isUser = userInfo.groups.includes('USER');
                                const isAdmin = userInfo.groups.includes('ADMIN');
                                const isSelf = userInfo.username === user?.username;

                                return (
                                    <tr key={userInfo.username}>
                                        <td className="username-cell">
                                            {userInfo.username}
                                            {isSelf && <span className="self-badge"> (You)</span>}
                                        </td>
                                        <td className="email-cell">{userInfo.email}</td>
                                        <td className="role-cell">
                                            <label className="role-toggle">
                                                <input
                                                    type="checkbox"
                                                    checked={isUser}
                                                    onChange={() => handleToggleRole(userInfo.username, 'USER', isUser)}
                                                />
                                                <span className="toggle-label">
                                                    {isUser ? '✓ USER' : 'Add USER'}
                                                </span>
                                            </label>
                                        </td>
                                        <td className="role-cell">
                                            <label className="role-toggle">
                                                <input
                                                    type="checkbox"
                                                    checked={isAdmin}
                                                    onChange={() => handleToggleRole(userInfo.username, 'ADMIN', isAdmin)}
                                                    disabled={isSelf && isAdmin}
                                                    title={isSelf && isAdmin ? 'Cannot remove yourself from ADMIN' : ''}
                                                />
                                                <span className="toggle-label">
                                                    {isAdmin ? '✓ ADMIN' : 'Add ADMIN'}
                                                </span>
                                            </label>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            {toast && (
                <Toast
                    message={toast.message}
                    type={toast.type}
                    onClose={() => setToast(null)}
                />
            )}
        </div>
    );
}
