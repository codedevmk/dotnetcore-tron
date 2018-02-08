﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace GossipNet.Messages
{
    public class AckMessage : GossipMessage
    {
        public AckMessage(int sequenceNumber)
        {
            SequenceNumber = sequenceNumber;
        }

        public int SequenceNumber { get; private set; }

        public override GossipMessageType Type
        {
            get { return GossipMessageType.Ack; }
        }
    }
}