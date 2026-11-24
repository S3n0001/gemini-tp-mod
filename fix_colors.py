import os
import codecs

def fix_file(filepath):
    """Fix corrupted UTF-8 color codes and remove BOM in a file"""
    try:
        # Read file as bytes first
        with open(filepath, 'rb') as f:
            raw_bytes = f.read()
        
        # Remove BOM if present
        if raw_bytes.startswith(codecs.BOM_UTF8):
            raw_bytes = raw_bytes[len(codecs.BOM_UTF8):]
            print(f"  Removed BOM from {os.path.basename(filepath)}")
        
        # Decode to string
        content = raw_bytes.decode('utf-8')
        
        # Replace corrupted color code
        original = content
        content = content.replace('Ã‚Â§', '§')
        
        if content != original:
            print(f"  Fixed color codes in {os.path.basename(filepath)}")
        
        # Write back without BOM (Python's 'utf-8' encoding doesn't add BOM by default)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        
        return True
    except Exception as e:
        print(f"Error processing {filepath}: {e}")
        return False

# Fix both files
files = [
    r'src\main\java\com\example\geminitp\TeleportCommand.java',
    r'src\main\java\com\example\geminitp\SystemPromptBuilder.java'
]

print("Fixing files...")
for file in files:
    if os.path.exists(file):
        fix_file(file)
    else:
        print(f"Not found: {file}")

print("\nDone!")
