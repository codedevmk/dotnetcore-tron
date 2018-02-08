package org.tron.core.witness;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@NoArgsConstructor
public class WitnessState implements Serializable {
  @Getter @Setter private byte[] address;
  @Getter @Setter private long voteCount;
  @Getter @Setter private byte[] pubKey;
  @Getter @Setter private String id;
  @Getter @Setter private String url;
  @Getter @Setter private long totalProduced;
  @Getter @Setter private long totalMissed;
  @Getter @Setter private long latestBlockNum;
}
