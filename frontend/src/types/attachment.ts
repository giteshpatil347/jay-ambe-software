export type AttachmentOwnerType = 'MATERIAL_TRANSACTION' | 'EXPENSE' | 'PARTY_PAYMENT';

export interface Attachment {
  id: number; ownerType: AttachmentOwnerType; ownerId: number; fileName: string; contentType: string;
  fileSize: number; sha256: string; uploadedBy: number | null; createdAt: string;
}
