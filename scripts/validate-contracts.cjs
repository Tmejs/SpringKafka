#!/usr/bin/env node
// Keep optional documentation tooling outside the application build.
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');

if (!process.argv[2]) {
    console.error('Usage: node scripts/validate-contracts.cjs PATH_TO_VALIDATOR_INSTALLATION');
    process.exit(2);
}
const tools = createRequire(path.resolve(process.argv[2], 'package.json'));
const SwaggerParser = tools('@apidevtools/swagger-parser');
const {Parser} = tools('@asyncapi/parser');
const root = path.resolve(__dirname, '..');

(async () => {
    const openapi = await SwaggerParser.validate(path.join(root, 'docs/api/openapi.yaml'));
    console.log(`OpenAPI ${openapi.openapi}: schema and references PASS`);
    const {document, diagnostics} = await new Parser().parse(
        fs.readFileSync(path.join(root, 'docs/api/asyncapi.yaml'), 'utf8'));
    for (const item of diagnostics) {
        console.log(`${['ERROR', 'WARNING', 'INFO', 'HINT'][item.severity]} ${item.code}: ${item.message}`);
    }
    if (!document || diagnostics.some(item => item.severity === 0)) {
        process.exitCode = 1;
    } else {
        console.log(`AsyncAPI ${document.version()}: parser validation PASS`);
    }
})().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
});
