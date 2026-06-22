// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormEvent } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Attachment } from '../../../shared/types';
import { MessageComposer } from './MessageComposer';

const attachment: Attachment = {
  storageKey: 'uploads/user/file.txt',
  fileUrl: 'http://localhost/file.txt',
  originalName: 'file.txt',
  mimeType: 'text/plain',
  fileSize: 12
};

describe('MessageComposer', () => {
  afterEach(() => {
    cleanup();
  });

  it('submits typed content and disables send when requested', async () => {
    const onChange = vi.fn();
    const onSubmit = vi.fn((event: FormEvent) => event.preventDefault());

    render(
      <MessageComposer
        value="hello"
        attachments={[]}
        placeholder="Message #general"
        uploadPending={false}
        sendDisabled={false}
        onChange={onChange}
        onSubmit={onSubmit}
        onFileSelected={vi.fn()}
        onRemoveAttachment={vi.fn()}
      />
    );

    await userEvent.type(screen.getByPlaceholderText('Message #general'), '!');
    await userEvent.click(screen.getByTitle('Send'));

    expect(onChange).toHaveBeenLastCalledWith('hello!');
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it('shows attachments and removes the selected item', async () => {
    const onRemoveAttachment = vi.fn();

    render(
      <MessageComposer
        value=""
        attachments={[attachment]}
        placeholder="Message #general"
        uploadPending={false}
        sendDisabled={false}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        onFileSelected={vi.fn()}
        onRemoveAttachment={onRemoveAttachment}
      />
    );

    expect(screen.getByText('file.txt')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: '' }));

    expect(onRemoveAttachment).toHaveBeenCalledWith(0);
  });

  it('passes selected files to the upload callback', async () => {
    const onFileSelected = vi.fn();
    const { container } = render(
      <MessageComposer
        value=""
        attachments={[]}
        placeholder="Message #general"
        uploadPending={false}
        sendDisabled={false}
        onChange={vi.fn()}
        onSubmit={vi.fn()}
        onFileSelected={onFileSelected}
        onRemoveAttachment={vi.fn()}
      />
    );

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' });
    await userEvent.upload(fileInput, file);

    expect(onFileSelected).toHaveBeenCalledWith(file);
  });
});
