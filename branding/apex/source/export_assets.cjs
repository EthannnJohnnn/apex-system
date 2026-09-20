// Rasterize the SVG masters using Sharp; create a multi-size PNG-backed ICO.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.APEX_NODE_MODULES + '/sharp');
const root = path.resolve(__dirname, '..');
async function main() {
  const pngDir = path.join(root, 'png');
  fs.mkdirSync(pngDir, {recursive:true});
  for (const theme of ['light','dark','black','white']) {
    await sharp(path.join(root,'svg',`apex-logo-${theme}.svg`),{density:288}).resize({width:2400}).png().toFile(path.join(pngDir,`apex-logo-${theme}.png`));
    await sharp(path.join(root,'svg',`apex-mark-${theme}.svg`),{density:288}).resize(1024,1024).png().toFile(path.join(pngDir,`apex-mark-${theme}.png`));
  }
  const frames = [];
  for (const size of [16,32,48,64]) {
    const data = await sharp(path.join(root,'favicon','favicon-light.svg'),{density:72*size/16}).resize(size,size).png().toBuffer();
    fs.writeFileSync(path.join(root,'favicon',`favicon-${size}.png`),data);
    frames.push({size,data});
  }
  const header = Buffer.alloc(6 + 16*frames.length);
  header.writeUInt16LE(1,2); header.writeUInt16LE(frames.length,4);
  let offset = header.length;
  frames.forEach(({size,data},i) => {
    const p = 6+i*16;
    header[p]=size; header[p+1]=size;
    header.writeUInt16LE(1,p+4); header.writeUInt16LE(32,p+6);
    header.writeUInt32LE(data.length,p+8); header.writeUInt32LE(offset,p+12);
    offset+=data.length;
  });
  fs.writeFileSync(path.join(root,'favicon','favicon.ico'),Buffer.concat([header,...frames.map(f=>f.data)]));
  for (const [file,size] of [['apple-touch-icon.png',180],['icon-192.png',192],['icon-512.png',512]]) {
    await sharp(path.join(root,'svg','apex-mark-light.svg'),{density:288}).resize(size,size).flatten({background:'#F5F7FA'}).png().toFile(path.join(root,'favicon',file));
  }
  await sharp(path.join(root,'apex-preview.svg')).png().toFile(path.join(root,'apex-preview.png'));
  for (const file of fs.readdirSync(pngDir)) {
    const img = sharp(path.join(pngDir,file));
    const meta=await img.metadata(); const stats=await img.stats();
    if(!meta.hasAlpha || stats.isOpaque) throw Error('Missing transparency: '+file);
    console.log(`${file}: ${meta.width}x${meta.height}, transparent`);
  }
  console.log('PNG, preview, and ICO exports verified.');
}
main().catch(err=>{console.error(err);process.exit(1)});
