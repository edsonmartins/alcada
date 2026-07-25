import { describe, expect, it } from "vitest";
import { limparId, RE_UUID } from "../api/config";

describe("limparId (sanitização de UUID do login)", () => {
  const uuid = "10133823-225f-4b3a-9e9b-edde42927da3";

  it("remove aspas coladas ao copiar de um .env", () => {
    expect(limparId(`"${uuid}"`)).toBe(uuid);
    expect(limparId(`'${uuid}'`)).toBe(uuid);
  });

  it("remove espaços em volta", () => {
    expect(limparId(`  ${uuid}\n`)).toBe(uuid);
  });

  it("o resultado limpo casa com o formato UUID", () => {
    expect(RE_UUID.test(limparId(`"${uuid}"`))).toBe(true);
    expect(RE_UUID.test("nao-e-uuid")).toBe(false);
  });
});
