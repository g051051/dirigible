
import { Digest } from 'sdk/utils/digest';
import { Assert } from 'test/assert';

const input = [41, 42, 43];
const result = Digest.sha512(input);

console.log(JSON.stringify(result));

Assert.assertTrue(result.length === 64 && result[0] === 123);
