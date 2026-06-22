import { FormEvent, useRef } from 'react';
import { Plus, Send, X } from 'lucide-react';
import type { Attachment } from '../../../shared/types';

export function MessageComposer({
  value,
  attachments,
  placeholder,
  uploadPending,
  sendDisabled,
  onChange,
  onSubmit,
  onFileSelected,
  onRemoveAttachment
}: {
  value: string;
  attachments: Attachment[];
  placeholder: string;
  uploadPending: boolean;
  sendDisabled: boolean;
  onChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onFileSelected: (file?: File) => void;
  onRemoveAttachment: (index: number) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <form className="composer" onSubmit={onSubmit}>
      <div style={{ display: 'flex', flexDirection: 'column', width: '100%' }}>
        <div className="chat-attachments-wrapper">
          {attachments.length > 0 && (
            <div className="attachments" style={{ marginBottom: '8px', padding: '0 8px' }}>
              {attachments.map((file, index) => (
                <span
                  key={file.storageKey || index}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '8px',
                    background: 'var(--tertiary)',
                    padding: '4px 8px',
                    borderRadius: '4px',
                    fontSize: '13px'
                  }}
                >
                  <span style={{ maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--white)' }}>
                    {file.originalName}
                  </span>
                  <button
                    type="button"
                    onClick={() => onRemoveAttachment(index)}
                    style={{ border: 0, background: 'transparent', color: '#fa777c', padding: 0, display: 'flex' }}
                  >
                    <X size={14} />
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="composer-input" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <input
            ref={fileInputRef}
            type="file"
            style={{ display: 'none' }}
            onChange={(event) => onFileSelected(event.currentTarget.files?.[0])}
          />

          <button
            type="button"
            title="Upload file"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadPending}
            style={{ border: 0, background: 'transparent', color: 'var(--gray)', padding: 0, display: 'flex', alignItems: 'center' }}
          >
            <Plus size={20} style={{ background: 'var(--primary)', borderRadius: '50%', padding: '2px', color: 'var(--white)' }} />
          </button>

          <input
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder={uploadPending ? 'Uploading attachment...' : placeholder}
            disabled={uploadPending}
          />
        </div>
      </div>

      <button title="Send" type="submit" disabled={sendDisabled}>
        <Send size={20} />
      </button>
    </form>
  );
}
