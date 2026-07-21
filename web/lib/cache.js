import { Expression } from "./expression.js";
import { PlainExpressionList } from "./expression-set.js";

/**
 * IndexedDB cache for generated expression lists (compact binary).
 */

const DB_NAME = "inequivalent-solver";
const DB_VERSION = 1;
const STORE = "expressionLists";

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: "n" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export function packList(expressionList) {
  const n = expressionList.numValues;
  const count = expressionList.numExpressions;
  const orderLen = 2 * n - 1;
  const opsLen = Math.max(n - 1, 0);

  const valueOrders = new Uint8Array(count * n);
  const operations = new Uint8Array(count * opsLen);
  const orders = new Uint8Array(count * orderLen);

  for (let i = 0; i < count; i++) {
    const e = expressionList.get(i);
    valueOrders.set(e.valueOrder, i * n);
    if (opsLen) operations.set(e.operations, i * opsLen);
    for (let j = 0; j < orderLen; j++) {
      orders[i * orderLen + j] = e.order[j] ? 1 : 0;
    }
  }

  return {
    n,
    count,
    valueOrders: valueOrders.buffer,
    operations: operations.buffer,
    orders: orders.buffer,
  };
}

export function unpackList(record) {
  const n = record.n;
  const count = record.count;
  const orderLen = 2 * n - 1;
  const opsLen = Math.max(n - 1, 0);
  const valueOrders = new Uint8Array(record.valueOrders);
  const operations = new Uint8Array(record.operations);
  const orders = new Uint8Array(record.orders);

  const expressions = new Array(count);
  for (let i = 0; i < count; i++) {
    const vo = Array.from(valueOrders.subarray(i * n, i * n + n));
    const ops = opsLen ? Array.from(operations.subarray(i * opsLen, i * opsLen + opsLen)) : [];
    const ord = Array.from(orders.subarray(i * orderLen, i * orderLen + orderLen), (x) => x === 1);
    expressions[i] = new Expression(vo, ops, ord);
  }
  return new PlainExpressionList(expressions, n);
}

export async function saveList(expressionList) {
  const db = await openDb();
  const packed = packList(expressionList);
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(packed);
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => reject(tx.error);
  });
}

export async function loadList(n) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).get(n);
    req.onsuccess = () => {
      db.close();
      resolve(req.result ? unpackList(req.result) : null);
    };
    req.onerror = () => reject(req.error);
  });
}

export async function clearCache() {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).clear();
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => reject(tx.error);
  });
}

export async function hasFullCache(maxN) {
  for (let n = 1; n <= maxN; n++) {
    const list = await loadList(n);
    if (!list) return false;
  }
  return true;
}
