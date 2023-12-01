import React, {useEffect, useState} from 'react';
import Button from 'react-bootstrap/Button';
import styled from "styled-components";
import Form from "react-bootstrap/Form";
import {RowWrapper} from "./SellComponent";
const AccountWrapper = styled.div`
  
  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`
const AccountComponent = () => {
    const [accountBalance, setAccountBalance] = useState(0);
    const [depositAmount, setDepositAmount] = useState(0);
    const updateBalance = () => {
        setAccountBalance(accountBalance + depositAmount);
    }

    const updateDepositAmount = (e) => {
        e.preventDefault();
        setDepositAmount(parseInt(e.target.value));
    }

    return (
        <AccountWrapper>
            <h1>Account Balance: {accountBalance}</h1>
                <Form.Label>Enter Amount to Deposit:</Form.Label>
                <Form.Control onChange={updateDepositAmount} name="amt" type="number" required/>
                <Button size="lg" onClick={updateBalance} variant="primary"> Add Funds </Button>
        </AccountWrapper>
    );
};

export default AccountComponent;
