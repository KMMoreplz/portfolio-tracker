'use strict';

const API_BASE = '/api/portfolios';
const TICKER_PATTERN = /^[A-Z0-9][A-Z0-9._-]{0,31}$/;
const CURRENCY_PATTERN = /^[A-Z]{3}$/;
const NUMBER_PATTERN = /^\d+(\.\d+)?$/;

const state = {
    portfolios: [],
    selectedPortfolioId: null,
    positions: [],
};

// Все присваивания innerHTML в этом файле — только очистка контейнеров
// пустой строкой (''); данные из API вставляются исключительно через textContent.

// ---------------------------------------------------------------------------
// Работа с API
// ---------------------------------------------------------------------------

class ApiError extends Error {
    constructor(status, body) {
        super(body && body.message ? body.message : `Сервер ответил ошибкой ${status}`);
        this.status = status;
        this.body = body || {};
        this.fieldErrors = Array.isArray(this.body.fieldErrors) ? this.body.fieldErrors : [];
    }
}

async function apiRequest(path, options = {}) {
    let response;
    try {
        response = await fetch(path, {
            ...options,
            headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        });
    } catch {
        throw new ApiError(0, { message: 'Нет связи с сервером: backend недоступен или перезапускается' });
    }
    const text = await response.text();
    let body = null;
    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = null;
        }
    }
    if (!response.ok) {
        throw new ApiError(response.status, body || { message: `Сервер ответил ошибкой ${response.status}` });
    }
    return body;
}

// ---------------------------------------------------------------------------
// Ошибки и уведомления
// ---------------------------------------------------------------------------

function showError(error) {
    const banner = document.getElementById('global-error');
    document.getElementById('global-error-message').textContent = error.message;
    const fields = document.getElementById('global-error-fields');
    fields.innerHTML = '';
    error.fieldErrors.forEach((item) => {
        const li = document.createElement('li');
        li.textContent = `${item.field}: ${item.message}`;
        fields.appendChild(li);
    });
    banner.hidden = false;
    document.getElementById('global-notice').hidden = true;
}

function clearError() {
    document.getElementById('global-error').hidden = true;
}

function showNotice(message) {
    document.getElementById('global-notice-message').textContent = message;
    document.getElementById('global-notice').hidden = false;
    document.getElementById('global-error').hidden = true;
}

function clearNotice() {
    document.getElementById('global-notice').hidden = true;
}

function showFormErrors(containerId, error) {
    const container = document.getElementById(containerId);
    container.innerHTML = '';
    const lines = [error.message];
    error.fieldErrors.forEach((item) => lines.push(`${item.field}: ${item.message}`));
    lines.forEach((line) => {
        const div = document.createElement('div');
        div.textContent = line;
        container.appendChild(div);
    });
    container.hidden = false;
}

function clearFormErrors(containerId) {
    const container = document.getElementById(containerId);
    container.innerHTML = '';
    container.hidden = true;
}

// ---------------------------------------------------------------------------
// Индикаторы загрузки
// ---------------------------------------------------------------------------

function setBusy(busy, label) {
    const overlay = document.getElementById('busy-overlay');
    overlay.hidden = !busy;
    if (busy && label) {
        document.getElementById('busy-label').textContent = label;
    }
}

function setButtonBusy(button, busy, busyLabel) {
    if (busy) {
        button.dataset.originalLabel = button.textContent;
        button.textContent = busyLabel;
        button.disabled = true;
    } else {
        button.textContent = button.dataset.originalLabel || button.textContent;
        button.disabled = false;
    }
}

// ---------------------------------------------------------------------------
// Форматирование
// ---------------------------------------------------------------------------

const numberFormatter = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 8 });
const percentFormatter = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });

function formatNumber(value) {
    return numberFormatter.format(Number(value));
}

function formatPercent(value) {
    return `${percentFormatter.format(value)} %`;
}

function formatDate(value) {
    if (!value) {
        return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

// ---------------------------------------------------------------------------
// Показатели (считаются только из данных, пришедших из API)
// ---------------------------------------------------------------------------

function computeMetrics(positions) {
    const investedByCurrency = new Map();
    const costByPosition = new Map();
    positions.forEach((position) => {
        const cost = Number(position.quantity) * Number(position.averagePrice);
        costByPosition.set(position.id, cost);
        investedByCurrency.set(
            position.currency,
            (investedByCurrency.get(position.currency) || 0) + cost,
        );
    });
    const shareByPosition = new Map();
    positions.forEach((position) => {
        const total = investedByCurrency.get(position.currency) || 0;
        const cost = costByPosition.get(position.id) || 0;
        shareByPosition.set(position.id, total > 0 ? (cost / total) * 100 : 0);
    });
    return { investedByCurrency, costByPosition, shareByPosition };
}

// ---------------------------------------------------------------------------
// Отрисовка: портфели
// ---------------------------------------------------------------------------

function renderPortfolios() {
    const list = document.getElementById('portfolio-list');
    const empty = document.getElementById('portfolio-empty');
    list.innerHTML = '';
    empty.hidden = state.portfolios.length > 0;
    list.hidden = state.portfolios.length === 0;

    state.portfolios.forEach((portfolio) => {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'portfolio-item';
        if (portfolio.id === state.selectedPortfolioId) {
            button.classList.add('active');
        }
        const name = document.createElement('span');
        name.className = 'portfolio-item-name';
        name.textContent = portfolio.name;
        const currency = document.createElement('span');
        currency.className = 'badge';
        currency.textContent = portfolio.baseCurrency;
        button.append(name, currency);
        button.addEventListener('click', () => selectPortfolio(portfolio.id));
        item.appendChild(button);
        list.appendChild(item);
    });
}

function renderPortfolioHeader() {
    const portfolio = state.portfolios.find((item) => item.id === state.selectedPortfolioId);
    const placeholder = document.getElementById('detail-placeholder');
    const content = document.getElementById('detail-content');

    if (!portfolio) {
        placeholder.hidden = false;
        content.hidden = true;
        return;
    }
    placeholder.hidden = true;
    content.hidden = false;
    document.getElementById('portfolio-title').textContent = portfolio.name;
    document.getElementById('portfolio-subtitle').textContent =
        `Базовая валюта: ${portfolio.baseCurrency} · создан ${formatDate(portfolio.createdAt)}`;
}

// ---------------------------------------------------------------------------
// Отрисовка: позиции и показатели
// ---------------------------------------------------------------------------

function renderPositions() {
    const body = document.getElementById('positions-body');
    const empty = document.getElementById('positions-empty');
    body.innerHTML = '';
    empty.hidden = state.positions.length > 0;

    const metrics = computeMetrics(state.positions);
    state.positions.forEach((position) => {
        const row = document.createElement('tr');

        const ticker = document.createElement('td');
        ticker.className = 'mono';
        ticker.textContent = position.ticker;

        const instrumentName = document.createElement('td');
        instrumentName.textContent = position.instrumentName || '—';

        const quantity = document.createElement('td');
        quantity.className = 'num mono';
        quantity.textContent = formatNumber(position.quantity);

        const averagePrice = document.createElement('td');
        averagePrice.className = 'num mono';
        averagePrice.textContent = formatNumber(position.averagePrice);

        const currency = document.createElement('td');
        currency.textContent = position.currency;

        const cost = document.createElement('td');
        cost.className = 'num mono';
        cost.textContent = formatNumber(metrics.costByPosition.get(position.id));

        const share = document.createElement('td');
        share.className = 'num mono';
        share.textContent = formatPercent(metrics.shareByPosition.get(position.id));

        const actions = document.createElement('td');
        actions.className = 'row-actions';
        const editButton = document.createElement('button');
        editButton.type = 'button';
        editButton.className = 'btn btn-small';
        editButton.textContent = 'Изменить';
        editButton.addEventListener('click', () => openPositionDialog(position));
        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'btn btn-small btn-danger';
        deleteButton.textContent = 'Удалить';
        deleteButton.addEventListener('click', () => deletePosition(position));
        actions.append(editButton, deleteButton);

        row.append(ticker, instrumentName, quantity, averagePrice, currency, cost, share, actions);
        body.appendChild(row);
    });

    renderMetrics(metrics);
}

function renderMetrics(metrics) {
    document.getElementById('metric-count').textContent = String(state.positions.length);

    const invested = document.getElementById('metric-invested');
    invested.innerHTML = '';
    if (metrics.investedByCurrency.size === 0) {
        invested.textContent = '—';
        return;
    }
    metrics.investedByCurrency.forEach((amount, currency) => {
        const line = document.createElement('span');
        line.className = 'metric-line';
        line.textContent = `${formatNumber(amount)} ${currency}`;
        invested.appendChild(line);
    });
}

async function loadPositions() {
    const loading = document.getElementById('positions-loading');
    const body = document.getElementById('detail-body');
    loading.hidden = false;
    body.hidden = true;
    clearError();
    try {
        state.positions = await apiRequest(`${API_BASE}/${state.selectedPortfolioId}/positions`);
        renderPositions();
        body.hidden = false;
    } catch (error) {
        showError(error);
        if (error.status === 404) {
            state.selectedPortfolioId = null;
            await loadPortfolios();
            renderPortfolioHeader();
        }
    } finally {
        loading.hidden = true;
    }
}

// ---------------------------------------------------------------------------
// Действия с портфелями
// ---------------------------------------------------------------------------

async function loadPortfolios() {
    const loading = document.getElementById('portfolios-loading');
    loading.hidden = false;
    clearError();
    try {
        state.portfolios = await apiRequest(API_BASE);
        if (state.selectedPortfolioId !== null
            && !state.portfolios.some((item) => item.id === state.selectedPortfolioId)) {
            state.selectedPortfolioId = null;
            state.positions = [];
            renderPortfolioHeader();
        }
        renderPortfolios();
    } catch (error) {
        showError(error);
    } finally {
        loading.hidden = true;
    }
}

async function selectPortfolio(id) {
    state.selectedPortfolioId = id;
    renderPortfolios();
    renderPortfolioHeader();
    await loadPositions();
}

async function deletePortfolio() {
    const portfolio = state.portfolios.find((item) => item.id === state.selectedPortfolioId);
    if (!portfolio) {
        return;
    }
    const confirmed = window.confirm(
        `Удалить портфель «${portfolio.name}» вместе со всеми позициями? Это действие необратимо.`,
    );
    if (!confirmed) {
        return;
    }
    setBusy(true, 'Удаление портфеля…');
    try {
        await apiRequest(`${API_BASE}/${portfolio.id}`, { method: 'DELETE' });
        state.selectedPortfolioId = null;
        state.positions = [];
        renderPortfolioHeader();
        await loadPortfolios();
        showNotice(`Портфель «${portfolio.name}» удалён`);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
    }
}

// ---------------------------------------------------------------------------
// Диалог портфеля (создание и переименование)
// ---------------------------------------------------------------------------

let portfolioDialogMode = 'create';

function openPortfolioDialog(mode) {
    portfolioDialogMode = mode;
    clearFormErrors('portfolio-form-errors');
    const dialog = document.getElementById('portfolio-dialog');
    const nameInput = document.getElementById('portfolio-name');
    const currencyInput = document.getElementById('portfolio-currency');
    const portfolio = state.portfolios.find((item) => item.id === state.selectedPortfolioId);

    if (mode === 'rename' && portfolio) {
        document.getElementById('portfolio-dialog-title').textContent = 'Переименование портфеля';
        nameInput.value = portfolio.name;
        currencyInput.value = portfolio.baseCurrency;
    } else {
        document.getElementById('portfolio-dialog-title').textContent = 'Новый портфель';
        nameInput.value = '';
        currencyInput.value = '';
    }
    dialog.showModal();
    nameInput.focus();
}

async function submitPortfolioForm(event) {
    event.preventDefault();
    clearFormErrors('portfolio-form-errors');

    const name = document.getElementById('portfolio-name').value.trim();
    const currencyRaw = document.getElementById('portfolio-currency').value.trim().toUpperCase();

    const localErrors = [];
    if (!name) {
        localErrors.push('название портфеля не может быть пустым');
    } else if (name.length > 100) {
        localErrors.push('название портфеля не длиннее 100 символов');
    }
    if (currencyRaw && !CURRENCY_PATTERN.test(currencyRaw)) {
        localErrors.push('базовая валюта — трёхбуквенный код ISO 4217 в верхнем регистре');
    }
    if (localErrors.length > 0) {
        showFormErrors('portfolio-form-errors', { message: 'Проверьте форму', fieldErrors: localErrors.map((message) => ({ field: 'форма', message })) });
        return;
    }

    const submit = document.getElementById('portfolio-form-submit');
    const payload = { name };
    if (currencyRaw) {
        payload.baseCurrency = currencyRaw;
    }

    setButtonBusy(submit, true, 'Сохранение…');
    try {
        if (portfolioDialogMode === 'rename' && state.selectedPortfolioId !== null) {
            await apiRequest(`${API_BASE}/${state.selectedPortfolioId}`, {
                method: 'PUT',
                body: JSON.stringify(payload),
            });
            document.getElementById('portfolio-dialog').close();
            await loadPortfolios();
            renderPortfolioHeader();
            showNotice('Портфель обновлён');
        } else {
            const created = await apiRequest(API_BASE, {
                method: 'POST',
                body: JSON.stringify(payload),
            });
            document.getElementById('portfolio-dialog').close();
            state.selectedPortfolioId = created.id;
            state.positions = [];
            await loadPortfolios();
            renderPortfolioHeader();
            await loadPositions();
            showNotice(`Портфель «${created.name}» создан`);
        }
    } catch (error) {
        showFormErrors('portfolio-form-errors', error);
    } finally {
        setButtonBusy(submit, false);
    }
}

// ---------------------------------------------------------------------------
// Диалог позиции (добавление и изменение)
// ---------------------------------------------------------------------------

let positionDialogMode = 'create';
let editingPositionId = null;

function openPositionDialog(position) {
    positionDialogMode = position ? 'edit' : 'create';
    editingPositionId = position ? position.id : null;
    clearFormErrors('position-form-errors');

    const dialog = document.getElementById('position-dialog');
    const tickerInput = document.getElementById('position-ticker');

    if (position) {
        document.getElementById('position-dialog-title').textContent = 'Изменение позиции';
        tickerInput.value = position.ticker;
        tickerInput.disabled = true;
        document.getElementById('position-instrument-name').value = position.instrumentName || '';
        document.getElementById('position-quantity').value = String(position.quantity);
        document.getElementById('position-average-price').value = String(position.averagePrice);
        document.getElementById('position-currency').value = position.currency;
    } else {
        document.getElementById('position-dialog-title').textContent = 'Новая позиция';
        tickerInput.value = '';
        tickerInput.disabled = false;
        document.getElementById('position-instrument-name').value = '';
        document.getElementById('position-quantity').value = '';
        document.getElementById('position-average-price').value = '';
        document.getElementById('position-currency').value = '';
    }
    dialog.showModal();
    tickerInput.focus();
}

async function submitPositionForm(event) {
    event.preventDefault();
    clearFormErrors('position-form-errors');

    const ticker = document.getElementById('position-ticker').value.trim().toUpperCase();
    const instrumentName = document.getElementById('position-instrument-name').value.trim();
    const quantityRaw = document.getElementById('position-quantity').value.trim();
    const averagePriceRaw = document.getElementById('position-average-price').value.trim();
    const currencyRaw = document.getElementById('position-currency').value.trim().toUpperCase();

    const localErrors = [];
    if (positionDialogMode === 'create') {
        if (!ticker) {
            localErrors.push('тикер не может быть пустым');
        } else if (!TICKER_PATTERN.test(ticker)) {
            localErrors.push('тикер — до 32 символов: заглавные буквы, цифры, точка, дефис, подчёркивание');
        }
    }
    if (!NUMBER_PATTERN.test(quantityRaw) || Number(quantityRaw) <= 0) {
        localErrors.push('количество должно быть числом больше нуля');
    }
    if (!NUMBER_PATTERN.test(averagePriceRaw) || Number(averagePriceRaw) < 0) {
        localErrors.push('средняя цена не может быть отрицательной');
    }
    if (currencyRaw && !CURRENCY_PATTERN.test(currencyRaw)) {
        localErrors.push('валюта — трёхбуквенный код ISO 4217 в верхнем регистре');
    }
    if (instrumentName.length > 200) {
        localErrors.push('название инструмента не длиннее 200 символов');
    }
    if (localErrors.length > 0) {
        showFormErrors('position-form-errors', {
            message: 'Проверьте форму',
            fieldErrors: localErrors.map((message) => ({ field: 'форма', message })),
        });
        return;
    }

    const payload = {
        quantity: quantityRaw,
        averagePrice: averagePriceRaw,
    };
    if (positionDialogMode === 'create') {
        payload.ticker = ticker;
        payload.instrumentName = instrumentName || null;
    } else {
        payload.instrumentName = instrumentName || null;
    }
    if (currencyRaw) {
        payload.currency = currencyRaw;
    }

    const submit = document.getElementById('position-form-submit');
    setButtonBusy(submit, true, 'Сохранение…');
    try {
        if (positionDialogMode === 'edit') {
            await apiRequest(`${API_BASE}/${state.selectedPortfolioId}/positions/${editingPositionId}`, {
                method: 'PUT',
                body: JSON.stringify(payload),
            });
        } else {
            await apiRequest(`${API_BASE}/${state.selectedPortfolioId}/positions`, {
                method: 'POST',
                body: JSON.stringify(payload),
            });
        }
        document.getElementById('position-dialog').close();
        await loadPositions();
        showNotice(positionDialogMode === 'edit' ? 'Позиция обновлена' : 'Позиция добавлена');
    } catch (error) {
        showFormErrors('position-form-errors', error);
    } finally {
        setButtonBusy(submit, false);
    }
}

async function deletePosition(position) {
    const confirmed = window.confirm(`Удалить позицию ${position.ticker}?`);
    if (!confirmed) {
        return;
    }
    setBusy(true, 'Удаление позиции…');
    try {
        await apiRequest(`${API_BASE}/${state.selectedPortfolioId}/positions/${position.id}`, {
            method: 'DELETE',
        });
        await loadPositions();
        showNotice(`Позиция ${position.ticker} удалена`);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
    }
}

// ---------------------------------------------------------------------------
// Инициализация
// ---------------------------------------------------------------------------

function bindEvents() {
    document.getElementById('btn-create-portfolio').addEventListener('click', () => openPortfolioDialog('create'));
    document.getElementById('btn-rename-portfolio').addEventListener('click', () => openPortfolioDialog('rename'));
    document.getElementById('btn-delete-portfolio').addEventListener('click', deletePortfolio);
    document.getElementById('btn-add-position').addEventListener('click', () => openPositionDialog(null));

    document.getElementById('portfolio-form').addEventListener('submit', submitPortfolioForm);
    document.getElementById('position-form').addEventListener('submit', submitPositionForm);

    document.querySelectorAll('[data-close-dialog]').forEach((button) => {
        button.addEventListener('click', () => button.closest('dialog').close());
    });

    document.getElementById('global-error-close').addEventListener('click', clearError);
    document.getElementById('global-notice-close').addEventListener('click', clearNotice);

    document.querySelectorAll('.uppercase').forEach((input) => {
        input.addEventListener('input', () => {
            input.value = input.value.toUpperCase();
        });
    });
}

bindEvents();
loadPortfolios();
