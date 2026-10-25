"""Inspect LOAD segment alignment for every native ELF in an APK/AAR. No third-party packages."""
import json,pathlib,struct,sys,zipfile
path=pathlib.Path(sys.argv[1])
records=[]
with zipfile.ZipFile(path) as archive:
 for name in archive.namelist():
  if not name.endswith('.so'):continue
  data=archive.read(name)
  if data[:4]!=b'\x7fELF':continue
  is64=data[4]==2
  endian='<' if data[5]==1 else '>'
  if not is64:continue
  phoff=struct.unpack_from(endian+'Q',data,32)[0]
  size,count=struct.unpack_from(endian+'HH',data,54)
  aligns=[struct.unpack_from(endian+'Q',data,phoff+i*size+48)[0] for i in range(count) if struct.unpack_from(endian+'I',data,phoff+i*size)[0]==1]
  records.append({'library':name,'load_alignment':aligns,'16kb_elf_compatible':bool(aligns) and all(a>=16384 for a in aligns)})
print(json.dumps(records,indent=2))
sys.exit(0 if records and all(r['16kb_elf_compatible'] for r in records) else 1)
