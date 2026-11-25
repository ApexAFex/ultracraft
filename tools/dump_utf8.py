import glob
import io

for path in glob.glob('tools/dumps/*.txt'):
    data = io.open(path, 'rb').read()
    if data[:2] in (b'\xff\xfe', b'\xfe\xff'):
        text = data.decode('utf-16')
    else:
        text = data.decode('utf-8-sig')
    io.open(path, 'w', encoding='utf-8', newline='').write(text)
    print(path, len(text))
