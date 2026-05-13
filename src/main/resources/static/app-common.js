// Potential 데모 프론트 공용 헬퍼
// - access token 은 sessionStorage 에 보관 (refresh token 은 HttpOnly cookie)
// - api() 는 BaseResponse<T> 봉투를 자동 해제하고 data 만 반환
// - 401 응답 시 자동으로 /v1/auth/refresh 1회 시도 후 재요청

(function (global) {
    const TOKEN_KEY = "accessToken";

    function getToken() {
        return sessionStorage.getItem(TOKEN_KEY);
    }

    function setToken(token) {
        if (token) {
            sessionStorage.setItem(TOKEN_KEY, token);
        } else {
            sessionStorage.removeItem(TOKEN_KEY);
        }
    }

    function isLoggedIn() {
        return Boolean(getToken());
    }

    // 동시 다발 401 시 refresh 중복 호출 방지 (single-flight)
    let refreshPromise = null;

    async function refreshAccessToken() {
        if (refreshPromise) return refreshPromise;
        refreshPromise = (async () => {
            try {
                const res = await fetch("/v1/auth/refresh", {
                    method: "POST",
                    credentials: "include"
                });
                if (!res.ok) return null;
                const body = await res.json().catch(() => null);
                if (!body || body.success === false) return null;
                const token = body.data && body.data.accessToken;
                if (token) setToken(token);
                return token;
            } finally {
                refreshPromise = null;
            }
        })();
        return refreshPromise;
    }

    function buildHeaders(extra) {
        const headers = Object.assign({}, extra || {});
        const token = getToken();
        if (token) headers["Authorization"] = "Bearer " + token;
        return headers;
    }

    async function rawFetch(path, options) {
        const opts = Object.assign({ method: "GET", credentials: "include" }, options || {});
        const hasBody = opts.body !== undefined && opts.body !== null;
        const headers = buildHeaders(opts.headers);
        if (hasBody && typeof opts.body !== "string") {
            headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(opts.body);
        }
        opts.headers = headers;
        return fetch(path, opts);
    }

    async function api(path, options) {
        let res = await rawFetch(path, options);
        if (res.status === 401 && getToken()) {
            const refreshed = await refreshAccessToken();
            if (refreshed) {
                res = await rawFetch(path, options);
            } else {
                // refresh 실패 → stale token 정리 (isLoggedIn() 거짓 양성 방지)
                setToken(null);
            }
        }
        const text = await res.text();
        let body = null;
        if (text) {
            try {
                body = JSON.parse(text);
            } catch (_) {
                body = null;
            }
        }
        if (!res.ok) {
            const message = (body && body.message) || ("요청 실패 (" + res.status + ")");
            const err = new Error(message);
            err.status = res.status;
            err.body = body;
            throw err;
        }
        if (body && typeof body === "object" && "success" in body) {
            if (body.success === false) {
                const err = new Error(body.message || "요청 실패");
                err.status = res.status;
                err.body = body;
                throw err;
            }
            return body.data;
        }
        return body;
    }

    async function login(email, password) {
        const data = await api("/v1/auth/login", {
            method: "POST",
            body: { email, password }
        });
        if (data && data.accessToken) setToken(data.accessToken);
        return data;
    }

    async function logout() {
        try {
            await api("/v1/auth/logout", { method: "POST" });
        } catch (err) {
            // 401/403: 이미 만료된 세션 — 정상 흐름
            // 그 외(네트워크/5xx): 클라이언트 토큰만 지우되 콘솔에 경고를 남겨 추적 가능하게
            if (err.status !== 401 && err.status !== 403) {
                console.warn("logout 요청 실패, 로컬 토큰만 삭제합니다", err);
            }
        }
        setToken(null);
    }

    function formatPrice(value) {
        if (value === null || value === undefined) return "-";
        const n = typeof value === "string" ? Number(value) : value;
        if (!Number.isFinite(n)) return String(value);
        return n.toLocaleString("ko-KR") + "원";
    }

    function formatDateTime(value) {
        if (!value) return "-";
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return value;
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        const hh = String(d.getHours()).padStart(2, "0");
        const mi = String(d.getMinutes()).padStart(2, "0");
        return `${yyyy}.${mm}.${dd} ${hh}:${mi}`;
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return "";
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    const COURSE_STATUS_LABEL = {
        PREPARATION: "준비중",
        OPEN: "모집중",
        CLOSED: "마감",
        CANCELLED: "취소"
    };
    const COURSE_LEVEL_LABEL = {
        BEGINNER: "입문",
        INTERMEDIATE: "중급",
        ADVANCED: "심화"
    };
    const ORDER_STATUS_LABEL = {
        PENDING: "결제대기",
        PAID: "결제완료",
        CONFIRMED: "확정",
        CANCELLED: "취소",
        REFUNDED: "환불",
        EXPIRED: "만료"
    };

    function statusLabel(status) {
        return COURSE_STATUS_LABEL[status] || status || "-";
    }

    function levelLabel(level) {
        return COURSE_LEVEL_LABEL[level] || level || "-";
    }

    function orderStatusLabel(status) {
        return ORDER_STATUS_LABEL[status] || status || "-";
    }

    function qs(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    function requireLogin(redirectAfter) {
        if (isLoggedIn()) return true;
        const next = redirectAfter || (window.location.pathname + window.location.search);
        window.location.href = "/login.html?next=" + encodeURIComponent(next);
        return false;
    }

    function renderNav(activeKey) {
        const items = [
            { key: "home", label: "코스", href: "/app.html" },
            { key: "mypage", label: "마이페이지", href: "/mypage.html", auth: true }
        ];
        const links = items
            .filter(item => !item.auth || isLoggedIn())
            .map(item => {
                const active = item.key === activeKey ? " active" : "";
                return `<a class="nav-link${active}" href="${item.href}">${item.label}</a>`;
            })
            .join("");
        const right = isLoggedIn()
            ? `<button type="button" class="nav-btn ghost" id="navLogoutBtn">로그아웃</button>`
            : `<a class="nav-btn primary" href="/login.html">로그인</a>`;
        return `
            <header class="nav">
                <a class="nav-brand" href="/app.html">Potential</a>
                <div class="nav-links">${links}</div>
                <div class="nav-right">${right}</div>
            </header>
        `;
    }

    /**
     * 슬라이딩 윈도우 페이지네이션 HTML 렌더링 (공용 utility)
     * - data-attr 로 클릭 이벤트 위임용 속성명 지정 (예: "page", "rp")
     * - max 개수만큼만 페이지 버튼 노출 (모바일 가로 오버플로 방지)
     *
     * @param {number} currentPage  현재 페이지 (0-based)
     * @param {number} totalPages   총 페이지 수
     * @param {Object} options      { max?: number, dataAttr?: string }
     * @returns {string} HTML 문자열 (페이지 1개 이하면 빈 문자열)
     */
    function buildPagination(currentPage, totalPages, options) {
        const opts = options || {};
        const max = opts.max || 7;
        const dataAttr = opts.dataAttr || "page";

        if (totalPages <= 1) return "";

        const buttons = [];
        let start = Math.max(0, currentPage - Math.floor(max / 2));
        let end = Math.min(totalPages, start + max);
        start = Math.max(0, end - max);

        const prevDisabled = currentPage === 0 ? "disabled" : "";
        const nextDisabled = currentPage >= totalPages - 1 ? "disabled" : "";

        buttons.push(`<button type="button" data-${dataAttr}="${currentPage - 1}" ${prevDisabled}>‹</button>`);
        for (let i = start; i < end; i++) {
            const cls = i === currentPage ? "active" : "";
            buttons.push(`<button type="button" data-${dataAttr}="${i}" class="${cls}">${i + 1}</button>`);
        }
        buttons.push(`<button type="button" data-${dataAttr}="${currentPage + 1}" ${nextDisabled}>›</button>`);
        return buttons.join("");
    }

    function bindNav() {
        const btn = document.getElementById("navLogoutBtn");
        if (btn) {
            btn.addEventListener("click", async () => {
                await logout();
                window.location.href = "/app.html";
            });
        }
    }

    global.Potential = {
        api,
        login,
        logout,
        getToken,
        setToken,
        isLoggedIn,
        formatPrice,
        formatDateTime,
        escapeHtml,
        statusLabel,
        levelLabel,
        orderStatusLabel,
        qs,
        requireLogin,
        renderNav,
        bindNav,
        buildPagination
    };
})(window);
