export type AvatarSpaceSource =
  | { kind: 'portrait'; portraitId: string; name: string }
  | { kind: 'local'; image: string; name: string };
