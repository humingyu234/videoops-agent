export interface R<T> {
  code: number;
  msg: string;
  data: T | null;
}

export type RuoYiResponse<T> = R<T>;

export interface PageResult<T> {
  total: number;
  rows: T[];
}
