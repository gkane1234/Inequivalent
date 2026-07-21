/** Four basic operations matching the Java Operation class. */

export const OPERATIONS = [
  { apply: (a, b) => a + b, commutative: true, name: "+" },
  { apply: (a, b) => a - b, commutative: false, name: "-" },
  { apply: (a, b) => a * b, commutative: true, name: "*" },
  { apply: (a, b) => (b !== 0 ? a / b : NaN), commutative: false, name: "/" },
];

/** Naive unique orderings counting commutativity: + * each once, - / each twice = 6. */
export const NUM_OPERATION_ORDERINGS = 6;

export function opName(opCode) {
  return OPERATIONS[opCode].name;
}
