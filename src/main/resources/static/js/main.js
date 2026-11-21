/**
 * Main JavaScript Controller for Dormitory Management System
 */

const getContextPath = () => {
    const path = window.location.pathname;
    const parts = path.split('/');
    if (parts.length > 1 && parts[1]) {
        const p = parts[1].toLowerCase();
        if (p === 'qlykytucxa' || p === 'qlkytucxa') {
            return '/' + parts[1];
        }
    }
    return '';
};

const CONTEXT_PATH = getContextPath();
const API_BASE = CONTEXT_PATH + '/api';

// Token & User Storage Helpers
const Auth = {
    getToken: () => localStorage.getItem('access_token'),
    getRefreshToken: () => localStorage.getItem('refresh_token'),
    getUser: () => {
        try {
            return JSON.parse(localStorage.getItem('current_user') || '{}');
        } catch (e) {
            return {};
        }
    },
    setAuth: (token, refreshToken, user) => {
        if (token) localStorage.setItem('access_token', token);
        if (refreshToken) localStorage.setItem('refresh_token', refreshToken);
        if (user) localStorage.setItem('current_user', JSON.stringify(user));
    },
    clear: () => {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('current_user');
    },
    isLoggedIn: () => !!localStorage.getItem('access_token'),
    // Giải mã payload JWT an toàn để đọc exp (thời điểm hết hạn)
    parseJwt: (token) => {
        try {
            if (!token) return null;
            const base64Url = token.split('.')[1];
            if (!base64Url) return null;
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join(''));
            return JSON.parse(jsonPayload);
        } catch (e) {
            return null;
        }
    },
    // Kiểm tra xem Access Token có sắp hết hạn (trong vòng bufferSeconds giây) hoặc đã hết hạn chưa
    isTokenExpiringSoon: (bufferSeconds = 90) => {
        const token = Auth.getToken();
        if (!token) return true;
        const payload = Auth.parseJwt(token);
        if (!payload || !payload.exp) return false;
        const nowSec = Math.floor(Date.now() / 1000);
        return (payload.exp - nowSec) <= bufferSeconds;
    },
    logout: async () => {
        const token = Auth.getToken();
        const refreshToken = Auth.getRefreshToken();
        if (token || refreshToken) {
            try {
                const headers = { 'Content-Type': 'application/json' };
                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }
                await fetch(`${API_BASE}/auth/logout`, {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({ token: token, refreshToken: refreshToken })
                });
            } catch (e) {
                console.warn('Logout API error:', e);
            }
        }
        Auth.clear();
        window.location.href = CONTEXT_PATH + '/login';
    }
};

// Quản lý tiến trình làm mới token dùng chung trong cùng tab và đồng bộ đa tab
let refreshPromise = null;

function waitForStorageTokenChange(oldRefreshToken, timeoutMs = 3000) {
    return new Promise((resolve) => {
        const start = Date.now();
        const timer = setInterval(() => {
            const currentRt = Auth.getRefreshToken();
            const currentAt = Auth.getToken();
            if (currentAt && currentRt && currentRt !== oldRefreshToken) {
                clearInterval(timer);
                resolve(true);
            } else if (Date.now() - start >= timeoutMs) {
                clearInterval(timer);
                resolve(false);
            }
        }, 200);
    });
}

async function tryRefreshSession() {
    if (refreshPromise) return refreshPromise;

    refreshPromise = (async () => {
        const refreshToken = Auth.getRefreshToken();
        if (!refreshToken) return false;

        try {
            const res = await fetch(`${API_BASE}/auth/refresh`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: refreshToken })
            });
            const data = await res.json().catch(() => null);

            if (res.ok && data && data.code === 100 && data.result && data.result.token) {
                const r = data.result;
                const existingUser = Auth.getUser();
                Auth.setAuth(r.token, r.refreshToken, {
                    ...existingUser,
                    username: r.username || existingUser.username,
                    role: r.role || existingUser.role,
                    roleName: r.roleName || existingUser.roleName,
                    fullName: r.fullName || existingUser.fullName,
                    userCode: r.userCode || existingUser.userCode
                });
                return true;
            }

            // Khi 2 tab cùng refresh đồng thời, request thứ 2 nhận TOKEN_INVALID (code 1018) trong khoảng ân hạn 10s:
            // Chờ kết quả của tab đầu cập nhật vào localStorage thay vì đẩy người dùng ra màn đăng nhập
            if (data && data.code === 1018) {
                const updatedByOtherTab = await waitForStorageTokenChange(refreshToken, 3000);
                if (updatedByOtherTab) {
                    return true;
                }
            }

            return false;
        } catch (err) {
            console.warn('Refresh token error:', err);
            return false;
        } finally {
            refreshPromise = null;
        }
    })();

    return refreshPromise;
}

// Helper bóc tách mảng dữ liệu từ kết quả API (hỗ trợ cả List trực tiếp lẫn Page_Response.data)
function unwrapList(res) {
    if (!res) return [];
    const r = res.result !== undefined ? res.result : res;
    if (Array.isArray(r)) return r;
    if (r && Array.isArray(r.data)) return r.data;
    if (r && Array.isArray(r.content)) return r.content;
    return [];
}
const extractItems = unwrapList;

// API Client Wrapper
async function apiFetch(endpoint, options = {}, isRetry = false) {
    const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint.startsWith('/') ? endpoint : '/' + endpoint}`;
    const isPublicAuthCall = endpoint.includes('/auth/login') || endpoint.includes('/auth/register') || endpoint.includes('/auth/refresh');

    // 1. Kiểm tra chủ động (Proactive check): Nếu token sắp hoặc đã hết hạn, tự động refresh trước khi gửi request
    if (!isPublicAuthCall && Auth.getToken() && Auth.getRefreshToken()) {
        if (Auth.isTokenExpiringSoon(60)) {
            await tryRefreshSession();
        }
    }

    const headers = { ...(options.headers || {}) };
    if (!(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }

    const token = Auth.getToken();
    if (token && !isPublicAuthCall) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(url, { ...options, headers });
        const data = await response.json().catch(() => null);

        // 2. Xử lý phản hồi không xác thực: HTTP 401 hoặc mã lỗi liên quan token (1004, 1017)
        const isAuthError = response.status === 401 || (data && (data.code === 1004 || data.code === 1017));
        if (isAuthError && !window.location.pathname.includes('/login') && !endpoint.includes('/auth/')) {
            if (!isRetry && await tryRefreshSession()) {
                // Làm mới thành công -> thử lại request ban đầu với token mới
                return apiFetch(endpoint, options, true);
            }
            const isPublicLanding = window.location.pathname === CONTEXT_PATH + '/' || window.location.pathname === CONTEXT_PATH;
            if (!isPublicLanding) {
                Auth.clear();
                window.location.href = CONTEXT_PATH + '/login';
            }
            throw new Error('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại');
        }

        if (!response.ok || (data && data.code && data.code !== 100)) {
            const errorMsg = (data && data.message) ? data.message : `Lỗi ${response.status}: Thao tác thất bại`;
            throw new Error(errorMsg);
        }

        // Tự động gán thuộc tính mảng tương thích ngược nếu result là Page_Response ({ data: [...] })
        if (data && data.result && !Array.isArray(data.result) && Array.isArray(data.result.data)) {
            data.pageInfo = data.result;
            data.result = data.result.data;
            try {
                data.result.pageInfo = data.pageInfo;
            } catch (e) {}
        }

        return data;
    } catch (err) {
        console.error('API Call Failed:', err);
        throw err;
    }
}

// 3. Cơ chế ngầm tự động duy trì phiên (Silent Auto-Refresh)
// - Định kỳ mỗi 60 giây kiểm tra: nếu token còn dưới 3 phút thì tự động refresh trong nền
// - Khi người dùng quay lại tab (tab focus hoặc visibilitychange), kiểm tra ngay
if (typeof window !== 'undefined') {
    setInterval(() => {
        if (Auth.isLoggedIn() && Auth.getRefreshToken() && Auth.isTokenExpiringSoon(180)) {
            tryRefreshSession();
        }
    }, 60000);

    const triggerWakeRefresh = () => {
        if (Auth.isLoggedIn() && Auth.getRefreshToken() && Auth.isTokenExpiringSoon(180)) {
            tryRefreshSession().catch(() => {});
        }
    };

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') triggerWakeRefresh();
    });

    window.addEventListener('focus', triggerWakeRefresh);
    window.addEventListener('pageshow', triggerWakeRefresh);
}

// SweetAlert2 Alerts
const Toast = Swal.mixin({
    toast: true,
    position: 'top-end',
    showConfirmButton: false,
    timer: 3000,
    timerProgressBar: true
});

function showToastSuccess(message) {
    Toast.fire({ icon: 'success', title: message });
}
window.showToastSuccess = showToastSuccess;

function showToastError(message) {
    Toast.fire({ icon: 'error', title: message });
}
window.showToastError = showToastError;

function showToastInfo(message) {
    Toast.fire({ icon: 'info', title: message });
}
window.showToastInfo = showToastInfo;

function showToastWarning(message) {
    Toast.fire({ icon: 'warning', title: message });
}
window.showToastWarning = showToastWarning;

async function showConfirm(title, text, confirmText = 'Đồng ý') {
    const result = await Swal.fire({
        title: title,
        text: text,
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#003366',
        cancelButtonColor: '#94a3b8',
        confirmButtonText: confirmText,
        cancelButtonText: 'Hủy bỏ'
    });
    return result.isConfirmed;
}

function formatCurrency(amount) {
    if (amount == null) return '0 đ';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr;
    return date.toLocaleDateString('vi-VN');
}

function escapeHtml(text) {
    if (text == null) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

const DEFAULT_AVATAR_SVG = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='32' fill='%23e2e8f0'/%3E%3Ccircle cx='32' cy='24' r='12' fill='%2394a3b8'/%3E%3Cpath d='M14 54c0-10 8-16 18-16s18 6 18 16' fill='%2394a3b8'/%3E%3C/svg%3E";

function resolveAvatarUrl(url) {
    if (!url || typeof url !== 'string' || !url.trim()) return '';
    const trimmed = url.trim();
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('data:')) {
        return trimmed;
    }
    if (trimmed.startsWith('/')) {
        if (CONTEXT_PATH && !trimmed.startsWith(CONTEXT_PATH)) {
            return CONTEXT_PATH + trimmed;
        }
        return trimmed;
    }
    return (CONTEXT_PATH ? CONTEXT_PATH + '/' : '/') + trimmed;
}

/**
 * Render thanh phân trang chuẩn (Server-side Pagination) dùng chung cho các bảng hàng nghìn bản ghi
 */
function renderPaginationBar(containerId, pageInfo, onPageChangeFnName) {
    const el = document.getElementById(containerId);
    if (!el) return;
    if (pageInfo && pageInfo.pageInfo) pageInfo = pageInfo.pageInfo;
    if (Array.isArray(pageInfo)) {
        pageInfo = {
            currentPage: 1,
            totalPages: 1,
            totalElements: pageInfo.length,
            pageSize: pageInfo.length || 10
        };
    }
    if (!pageInfo || pageInfo.totalElements === undefined) {
        el.innerHTML = '';
        return;
    }

    const currentPage = pageInfo.currentPage || 1;
    const totalPages = Math.max(pageInfo.totalPages || 1, 1);
    const totalElements = pageInfo.totalElements || 0;
    const pageSize = pageInfo.pageSize || 10;
    const startItem = totalElements === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endItem = Math.min(currentPage * pageSize, totalElements);

    let pagesHtml = '';
    const startPage = Math.max(1, currentPage - 2);
    const endPage = Math.min(totalPages, currentPage + 2);

    pagesHtml += `
        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}">
            <a class="page-link" href="javascript:void(0)" onclick="${onPageChangeFnName}(${currentPage - 1})">‹ Trước</a>
        </li>
    `;
    if (startPage > 1) {
        pagesHtml += `<li class="page-item"><a class="page-link" href="javascript:void(0)" onclick="${onPageChangeFnName}(1)">1</a></li>`;
        if (startPage > 2) pagesHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
    }
    for (let p = startPage; p <= endPage; p++) {
        pagesHtml += `
            <li class="page-item ${p === currentPage ? 'active' : ''}">
                <a class="page-link" href="javascript:void(0)" onclick="${onPageChangeFnName}(${p})">${p}</a>
            </li>
        `;
    }
    if (endPage < totalPages) {
        if (endPage < totalPages - 1) pagesHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        pagesHtml += `<li class="page-item"><a class="page-link" href="javascript:void(0)" onclick="${onPageChangeFnName}(${totalPages})">${totalPages}</a></li>`;
    }
    pagesHtml += `
        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}">
            <a class="page-link" href="javascript:void(0)" onclick="${onPageChangeFnName}(${currentPage + 1})">Sau ›</a>
        </li>
    `;

    el.innerHTML = `
        <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 px-3 py-2 border-top bg-light rounded-bottom-4">
            <div class="small text-muted">
                Hiển thị <b>${startItem}–${endItem}</b> trên tổng số <b class="text-dark">${totalElements}</b> bản ghi (Trang ${currentPage}/${totalPages})
            </div>
            <nav>
                <ul class="pagination pagination-sm mb-0">${pagesHtml}</ul>
            </nav>
        </div>
    `;
}

// Initialize User UI & Role-Based Access Control
document.addEventListener('DOMContentLoaded', () => {
    const currentPath = window.location.pathname;
    const isPublicPage = currentPath.includes('/login') || currentPath === CONTEXT_PATH + '/' || currentPath === CONTEXT_PATH;

    if (!isPublicPage) {
        if (!Auth.isLoggedIn()) {
            window.location.href = CONTEXT_PATH + '/login';
            return;
        }

        const user = Auth.getUser();
        const role = (user.role || 'ADMIN').toUpperCase();

        // 1. Phân quyền chuyển hướng (Route Guard theo Role & Quyền trong CSDL)
        if (role === 'STUDENT' && !currentPath.includes('/student-portal')) {
            window.location.href = CONTEXT_PATH + '/student-portal';
            return;
        } else if (role === 'STAFF' && (
            currentPath.includes('/settings') ||
            currentPath.includes('/admin/database') ||
            currentPath.includes('/admin/users') ||
            currentPath.includes('/admin/staff')
        )) {
            window.location.href = CONTEXT_PATH + '/dashboard';
            return;
        } else if (role === 'MANAGER' && currentPath.includes('/admin/database')) {
            window.location.href = CONTEXT_PATH + '/dashboard';
            return;
        }

        // 2. Ẩn/Hiện các thành phần giao diện & Menu Sidebar theo vai trò
        const showEls = (selector, show) => {
            document.querySelectorAll(selector).forEach(el => {
                if (show) el.classList.remove('d-none');
                else el.classList.add('d-none');
            });
        };

        if (role === 'STUDENT') {
            showEls('.role-admin-staff', false);
            showEls('.role-manager-up', false);
            showEls('.role-admin-only', false);
            showEls('.role-manager-only', false);
            showEls('.role-staff-only', false);
            showEls('.role-student-only', true);
        } else if (role === 'STAFF') {
            showEls('.role-student-only', false);
            showEls('.role-admin-only', false);
            showEls('.role-manager-up', false);
            showEls('.role-manager-only', false);
            showEls('.role-admin-staff', true);
            showEls('.role-staff-only', true);
        } else if (role === 'MANAGER') {
            showEls('.role-student-only', false);
            showEls('.role-admin-only', false);
            showEls('.role-staff-only', false);
            showEls('.role-admin-staff', true);
            showEls('.role-manager-up', true);
            showEls('.role-manager-only', true);
        } else { // ADMIN
            showEls('.role-student-only', false);
            showEls('.role-manager-only', false);
            showEls('.role-staff-only', false);
            showEls('.role-admin-staff', true);
            showEls('.role-manager-up', true);
            showEls('.role-admin-only', true);
        }

        // 3. Hiển thị thông tin người dùng lên Topbar & Sidebar
        const displayName = user.fullName || user.username || 'Người dùng';
        let roleName = user.roleName || 'Quản trị viên';
        let avatarBg = '#2563eb';
        let chipClass = 'bg-primary-subtle text-primary';

        if (role === 'MANAGER') {
            roleName = user.roleName || 'Trưởng Ban Quản Lý';
            avatarBg = '#0284c7';
            chipClass = 'bg-info-subtle text-info';
        } else if (role === 'STAFF') {
            roleName = user.roleName || 'Nhân viên KTX';
            avatarBg = '#7c3aed';
            chipClass = 'bg-purple-subtle text-purple';
        } else if (role === 'STUDENT') {
            roleName = user.roleName || 'Sinh viên nội trú';
            avatarBg = '#059669';
            chipClass = 'bg-success-subtle text-success';
        }

        document.querySelectorAll('.user-display-name').forEach(el => el.textContent = displayName);
        document.querySelectorAll('.user-display-role').forEach(el => el.textContent = roleName);
        document.querySelectorAll('.user-role-chip').forEach(el => {
            el.textContent = role;
            el.className = `badge rounded-pill user-role-chip ${chipClass}`;
        });
        document.querySelectorAll('.user-avatar-text').forEach(el => {
            el.textContent = displayName.charAt(0).toUpperCase();
            el.style.backgroundColor = avatarBg;
        });

        // 4. Khởi tạo Thông báo Topbar nếu có & tự động làm mới chu kỳ 25s
        loadTopbarNotifications().catch(() => {});
        if (!window.__notifIntervalStarted) {
            window.__notifIntervalStarted = true;
            setInterval(() => {
                if (Auth.isLoggedIn()) {
                    loadTopbarNotifications().catch(() => {});
                }
            }, 25000);
        }
    }
});

// =======================================================================
// HỘP THƯ THÔNG BÁO (Notification Inbox) - NotificationRecipient_Controller
// =======================================================================
// Định dạng thời gian tương đối cho thông báo (Vừa xong, x phút trước, ngày/tháng)
function formatNotificationTime(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr;
    const now = new Date();
    const diffMs = now - date;
    const diffMin = Math.floor(diffMs / (1000 * 60));
    const diffHour = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDay = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffMin < 1) return 'Vừa xong';
    if (diffMin < 60) return `${diffMin} phút trước`;
    if (diffHour < 24) return `${diffHour} giờ trước`;
    if (diffDay < 7) return `${diffDay} ngày trước`;

    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes} ${day}/${month}`;
}

async function loadTopbarNotifications() {
    if (!Auth.isLoggedIn()) return;
    try {
        const [cntRes, inboxRes] = await Promise.all([
            apiFetch('/notifications/inbox/unread-count').catch(() => ({ result: 0 })),
            apiFetch('/notifications/inbox?page=1&size=8').catch(() => ({ result: [] }))
        ]);

        const unreadCount = (cntRes && cntRes.result !== undefined) ? Number(cntRes.result) : 0;
        const badgeEls = document.querySelectorAll('.topbar-unread-badge');
        badgeEls.forEach(el => {
            if (unreadCount > 0) {
                el.textContent = unreadCount > 99 ? '99+' : unreadCount;
                el.classList.remove('d-none');
                el.style.display = 'inline-block';
            } else {
                el.classList.add('d-none');
                el.style.display = 'none';
            }
        });

        const list = unwrapList(inboxRes);
        const listEls = document.querySelectorAll('.topbar-notification-list');
        listEls.forEach(container => {
            if (!list || list.length === 0) {
                container.innerHTML = '<div class="text-center text-muted py-4 small"><i class="bi bi-bell-slash d-block fs-3 mb-2 text-secondary opacity-50"></i>Chưa có thông báo nào.</div>';
                return;
            }

            container.innerHTML = list.map(item => {
                const notif = item.notification || item;
                const isRead = item.isRead;
                const title = item.title || item.notificationTitle || (notif && notif.title) || 'Thông báo KTX';
                const content = item.content || item.notificationContent || (notif && notif.content) || '';
                const rawTime = item.createdAt || item.receivedAt || item.publishedAt || (notif && (notif.createdAt || notif.publishedAt));
                const timeStr = rawTime ? formatNotificationTime(rawTime) : '';

                // Xác định ngữ cảnh & điều hướng
                let iconClass = 'bi-bell-fill text-primary';
                let typeBadge = '<span class="badge bg-secondary-subtle text-secondary" style="font-size: 0.65rem;">Hệ thống</span>';
                let redirectUrl = '';
                let redirectBtnText = '';

                const combined = (title + ' ' + content).toLowerCase();
                const role = Auth.getUser() ? Auth.getUser().role : '';

                if (combined.includes('hợp đồng')) {
                    iconClass = 'bi-file-earmark-text-fill text-primary';
                    typeBadge = '<span class="badge bg-primary-subtle text-primary" style="font-size: 0.65rem;">Hợp đồng</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/contracts`;
                    redirectBtnText = (role === 'STUDENT') ? 'Xem hợp đồng của tôi' : 'Đi tới Quản lý Hợp đồng';
                } else if (combined.includes('sửa chữa') || combined.includes('bảo trì')) {
                    iconClass = 'bi-tools text-warning';
                    typeBadge = '<span class="badge bg-warning-subtle text-warning-emphasis" style="font-size: 0.65rem;">Bảo trì</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/maintenance`;
                    redirectBtnText = (role === 'STUDENT') ? 'Xem yêu cầu sửa chữa' : 'Đi tới Quản lý Sửa chữa';
                } else if (combined.includes('chuyển phòng')) {
                    iconClass = 'bi-arrow-left-right text-info';
                    typeBadge = '<span class="badge bg-info-subtle text-info-emphasis" style="font-size: 0.65rem;">Chuyển phòng</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/contracts`;
                    redirectBtnText = (role === 'STUDENT') ? 'Xem đơn đổi phòng' : 'Đi tới Quản lý Chuyển phòng';
                } else if (combined.includes('xe') || combined.includes('phương tiện')) {
                    iconClass = 'bi-bicycle text-success';
                    typeBadge = '<span class="badge bg-success-subtle text-success" style="font-size: 0.65rem;">Gửi xe</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/students`;
                    redirectBtnText = 'Xem chi tiết phương tiện';
                } else if (combined.includes('khách')) {
                    iconClass = 'bi-person-badge-fill text-dark';
                    typeBadge = '<span class="badge bg-dark-subtle text-dark" style="font-size: 0.65rem;">Khách thăm</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/students`;
                    redirectBtnText = 'Xem chi tiết khách thăm';
                } else if (combined.includes('phản hồi') || combined.includes('phản ánh') || combined.includes('góp ý')) {
                    iconClass = 'bi-chat-left-heart-fill text-purple';
                    typeBadge = '<span class="badge bg-purple-subtle text-purple" style="font-size: 0.65rem;">Phản ánh</span>';
                    redirectUrl = (role === 'STUDENT') ? `${CONTEXT_PATH}/student-portal` : `${CONTEXT_PATH}/dashboard`;
                    redirectBtnText = 'Xem chi tiết phản hồi';
                }

                return `
                    <div class="p-2 border-bottom notification-item ${isRead ? 'bg-white' : 'bg-primary-subtle bg-opacity-10'}" style="cursor: pointer; transition: background 0.15s;" onclick="handleNotificationClick('${item.recipientId}', '${encodeURIComponent(title)}', '${encodeURIComponent(content)}', '${encodeURIComponent(timeStr)}', '${encodeURIComponent(redirectUrl)}', '${encodeURIComponent(redirectBtnText)}')">
                        <div class="d-flex justify-content-between align-items-start gap-1 mb-1">
                            <div class="d-flex align-items-center gap-1 flex-wrap">
                                <i class="bi ${iconClass} me-1" style="font-size: 0.85rem;"></i>
                                ${typeBadge}
                                ${!isRead ? '<span class="badge bg-primary rounded-pill px-1" style="font-size: 0.6rem;">Mới</span>' : ''}
                            </div>
                            <span class="text-muted" style="font-size: 0.7rem; white-space: nowrap;">${timeStr}</span>
                        </div>
                        <div class="fw-semibold small text-dark ${!isRead ? 'fw-bold' : ''}" style="font-size: 0.82rem; line-height: 1.3;">
                            ${escapeHtml(title)}
                        </div>
                        <div class="small text-muted text-truncate mt-1" style="max-width: 290px; font-size: 0.78rem;">
                            ${escapeHtml(content || 'Nhấn để xem chi tiết thông báo')}
                        </div>
                    </div>
                `;
            }).join('');
        });
    } catch (e) {
        console.warn('Lỗi tải thông báo:', e);
    }
}

async function handleNotificationClick(recipientId, titleEncoded, contentEncoded, timeEncoded, redirectUrlEncoded, redirectTextEncoded) {
    const title = decodeURIComponent(titleEncoded || '');
    const content = decodeURIComponent(contentEncoded || '');
    const timeStr = decodeURIComponent(timeEncoded || '');
    const redirectUrl = decodeURIComponent(redirectUrlEncoded || '');
    const redirectText = decodeURIComponent(redirectTextEncoded || '');

    // Đánh dấu đã đọc qua API
    if (recipientId) {
        try {
            await apiFetch(`/notifications/inbox/${recipientId}/read`, { method: 'PUT' });
            loadTopbarNotifications().catch(() => {});
        } catch (e) {
            console.warn(e);
        }
    }

    const hasRedirect = redirectUrl && redirectUrl.length > 0;

    // Hiển thị nội dung chi tiết dạng SweetAlert2 chuyên nghiệp
    const result = await Swal.fire({
        title: `<div class="fs-6 fw-bold text-dark text-start border-bottom pb-2">${escapeHtml(title || 'Chi tiết Thông báo')}</div>`,
        html: `
            <div class="text-start mt-2">
                ${timeStr ? `<div class="small text-muted mb-2"><i class="bi bi-clock me-1"></i>Thời gian: <span class="fw-semibold text-dark">${escapeHtml(timeStr)}</span></div>` : ''}
                <div class="p-3 bg-light rounded-3 border text-secondary small" style="white-space: pre-wrap; line-height: 1.6; max-height: 250px; overflow-y: auto;">
                    ${escapeHtml(content || 'Không có nội dung chi tiết kèm theo.')}
                </div>
            </div>
        `,
        icon: 'info',
        showCancelButton: hasRedirect,
        confirmButtonText: hasRedirect ? `<i class="bi bi-box-arrow-up-right me-1"></i> ${redirectText || 'Đi tới trang xử lý'}` : 'Đã hiểu',
        confirmButtonColor: '#003366',
        cancelButtonText: 'Đóng',
        cancelButtonColor: '#6c757d',
        reverseButtons: true
    });

    if (result.isConfirmed && hasRedirect) {
        window.location.href = redirectUrl;
    }
}

async function markAllNotificationsRead() {
    try {
        await apiFetch('/notifications/inbox/read-all', { method: 'PUT' });
        showToastSuccess('Đã đánh dấu tất cả thông báo là đã đọc!');
        loadTopbarNotifications();
    } catch (e) {
        showToastError(e.message || 'Lỗi cập nhật');
    }
}

// =======================================================================
// HỒ SƠ CÁ NHÂN & ĐỔI MẬT KHẨU - User_Controller (/api/users/me)
// =======================================================================
let globalProfileModalInstance = null;

function toggleProfilePwd(inputId, iconId) {
    const input = document.getElementById(inputId);
    const icon = document.getElementById(iconId);
    if (!input || !icon) return;
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.remove('bi-eye');
        icon.classList.add('bi-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.remove('bi-eye-slash');
        icon.classList.add('bi-eye');
    }
}

async function openMyProfileModal() {
    const modalEl = document.getElementById('myProfileModal');
    if (!modalEl) {
        showToastError('Không tìm thấy hộp thoại hồ sơ cá nhân');
        return;
    }
    if (!globalProfileModalInstance) {
        globalProfileModalInstance = new bootstrap.Modal(modalEl);
    }

    try {
        const [meRes, profRes] = await Promise.all([
            apiFetch('/users/me').catch(() => ({})),
            apiFetch('/users/me/profile').catch(() => ({}))
        ]);

        const u = meRes.result || meRes || {};
        const p = profRes.result || profRes || {};

        if (document.getElementById('profileUsername')) document.getElementById('profileUsername').value = u.username || '';
        if (document.getElementById('profileFullName')) document.getElementById('profileFullName').value = p.fullName || u.fullName || u.username || '';
        if (document.getElementById('profileUserCode')) document.getElementById('profileUserCode').value = p.userCode || u.userCode || '-';
        if (document.getElementById('profileRoleName')) document.getElementById('profileRoleName').value = p.roleName || u.roleName || (u.role && u.role.roleName) || 'Người dùng';
        if (document.getElementById('profileEmail')) document.getElementById('profileEmail').value = u.email || '';
        if (document.getElementById('profilePhone')) document.getElementById('profilePhone').value = u.phone || '';
        if (document.getElementById('profileTelegramId')) document.getElementById('profileTelegramId').value = u.telegramChatId || '';
        window._initialProfileTelegramId = (u.telegramChatId || '').trim();
        const roleCode = ((u.role && u.role.roleCode) || u.roleCode || (Auth.getUser() && Auth.getUser().role) || '').toUpperCase();
        const tgGroup = document.getElementById('profileTelegramGroup');
        if (tgGroup) {
            tgGroup.style.display = (roleCode === 'ADMIN') ? '' : 'none';
        }
        if (document.getElementById('profileCurrentPassword')) document.getElementById('profileCurrentPassword').value = '';
        if (document.getElementById('profilePassword')) document.getElementById('profilePassword').value = '';
        if (document.getElementById('profileConfirmPassword')) document.getElementById('profileConfirmPassword').value = '';

        globalProfileModalInstance.show();
    } catch (e) {
        showToastError('Không thể tải thông tin hồ sơ: ' + e.message);
    }
}

async function submitUpdateMyProfile() {
    const email = document.getElementById('profileEmail') ? document.getElementById('profileEmail').value.trim() : '';
    const phone = document.getElementById('profilePhone') ? document.getElementById('profilePhone').value.trim() : '';
    const currentPassword = document.getElementById('profileCurrentPassword') ? document.getElementById('profileCurrentPassword').value : '';
    const password = document.getElementById('profilePassword') ? document.getElementById('profilePassword').value : '';
    const confirmPassword = document.getElementById('profileConfirmPassword') ? document.getElementById('profileConfirmPassword').value : '';
    const telegramChatId = document.getElementById('profileTelegramId') ? document.getElementById('profileTelegramId').value.trim() : null;

    if (!email) {
        showToastError('Email không được để trống!');
        return;
    }

    const payload = { email, phone };
    let isPasswordChanged = false;
    const isTelegramChanged = telegramChatId !== null && telegramChatId !== (window._initialProfileTelegramId || '');

    if (password && password.trim().length > 0) {
        if (!currentPassword || currentPassword.trim().length === 0) {
            showToastError('Vui lòng nhập mật khẩu hiện tại để xác thực trước khi đổi mật khẩu mới!');
            return;
        }
        if (password.trim().length < 6) {
            showToastError('Mật khẩu mới phải có tối thiểu 6 ký tự!');
            return;
        }
        if (confirmPassword !== undefined && password !== confirmPassword) {
            showToastError('Xác nhận mật khẩu mới không khớp!');
            return;
        }
        isPasswordChanged = true;
    }

    if (isTelegramChanged && (!currentPassword || currentPassword.trim().length === 0)) {
        showToastError('Vui lòng nhập mật khẩu hiện tại để xác thực thay đổi cấu hình Telegram 2FA!');
        return;
    }

    try {
        // 1. Cập nhật email, phone qua PUT /api/users/me
        const res = await apiFetch('/users/me', {
            method: 'PUT',
            body: JSON.stringify(payload)
        });

        // 2. Nếu có thay đổi Telegram Chat ID thì cập nhật kèm mật khẩu xác nhận
        if (isTelegramChanged) {
            await apiFetch('/users/me/2fa', {
                method: 'PATCH',
                body: JSON.stringify({
                    telegramChatId: telegramChatId,
                    password: currentPassword.trim()
                })
            });
            window._initialProfileTelegramId = telegramChatId;
        }

        // 3. Nếu có đổi mật khẩu thì gọi POST /api/users/me/change-password (kiểm tra mật khẩu cũ ở backend)
        if (isPasswordChanged) {
            await apiFetch('/users/me/change-password', {
                method: 'POST',
                body: JSON.stringify({
                    currentPassword: currentPassword,
                    newPassword: password.trim()
                })
            });
        }

        const updated = res.result || res || {};
        const curUser = Auth.getUser() || {};
        Auth.setAuth(null, null, {
            ...curUser,
            email: updated.email || curUser.email,
            phone: updated.phone || curUser.phone
        });

        if (globalProfileModalInstance) globalProfileModalInstance.hide();

        if (isPasswordChanged) {
            Swal.fire({
                icon: 'success',
                title: 'Đổi mật khẩu thành công!',
                text: 'Mật khẩu tài khoản đã được cập nhật thành công.',
                confirmButtonColor: '#003366'
            });
        } else {
            showToastSuccess('Cập nhật thông tin cá nhân thành công!');
        }
    } catch (e) {
        showToastError(e.message || 'Lỗi cập nhật hồ sơ');
    }
}

// =======================================================================
// RESPONSIVE SIDEBAR & BACKDROP CONTROLLER (Mobile & Tablet)
// =======================================================================
window.toggleSidebar = function() {
    const sidebar = document.getElementById('appSidebar');
    let backdrop = document.querySelector('.sidebar-backdrop');
    if (!backdrop) {
        backdrop = document.createElement('div');
        backdrop.className = 'sidebar-backdrop';
        document.body.appendChild(backdrop);
        backdrop.addEventListener('click', () => {
            if (sidebar) sidebar.classList.remove('show');
            backdrop.classList.remove('show');
        });
    }

    if (sidebar) {
        const isOpen = sidebar.classList.toggle('show');
        if (isOpen) {
            backdrop.classList.add('show');
        } else {
            backdrop.classList.remove('show');
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    const sidebar = document.getElementById('appSidebar');
    if (sidebar) {
        sidebar.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                if (window.innerWidth < 992) {
                    sidebar.classList.remove('show');
                    const backdrop = document.querySelector('.sidebar-backdrop');
                    if (backdrop) backdrop.classList.remove('show');
                }
            });
        });
    }
});
